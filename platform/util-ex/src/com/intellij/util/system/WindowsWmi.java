package com.intellij.util.system;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;
import static java.nio.charset.StandardCharsets.UTF_16LE;

@ApiStatus.Internal
public final class WindowsWmi {
  private static final int CLSCTX_INPROC_SERVER = 1;
  private static final int RPC_C_AUTHN_WINNT = 10;
  private static final int RPC_C_AUTHN_LEVEL_CALL = 3;
  private static final int RPC_C_IMP_LEVEL_IMPERSONATE = 3;
  private static final int WBEM_FLAG_RETURN_IMMEDIATELY = 0x10;
  private static final int WBEM_FLAG_FORWARD_ONLY = 0x20;
  private static final int WBEM_S_TIMEDOUT = 0x40004;
  private static final int VT_EMPTY = 0;
  private static final int VT_NULL = 1;
  private static final int VT_I4 = 3;
  private static final int VT_BSTR = 8;
  private static final int VT_BOOL = 11;
  private static final StructLayout VARIANT_LAYOUT = MemoryLayout.structLayout(
    JAVA_SHORT.withName("vt"), MemoryLayout.paddingLayout(6), MemoryLayout.structLayout(ADDRESS, ADDRESS).withName("value"));
  private static final UUID LOCATOR_CLASS = UUID.fromString("4590F811-1D3A-11D0-891F-00AA004B2E24");
  private static final UUID LOCATOR_INTERFACE = UUID.fromString("DC12A687-737F-11CF-884D-00AA004B2E24");
  private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

  private WindowsWmi() { }

  public static @NotNull List<Map<String, @Nullable Object>> query(@NotNull String namespace,
                                                                   @NotNull String className,
                                                                   @NotNull List<String> properties,
                                                                   int timeoutMillis) throws IOException, TimeoutException {
    if (!IDENTIFIER.matcher(className).matches() || properties.isEmpty()) {
      throw new IllegalArgumentException("Failed requirement.");
    }
    for (var property : properties) {
      if (!IDENTIFIER.matcher(property).matches()) throw new IllegalArgumentException("Failed requirement.");
    }
    if (timeoutMillis <= 0) throw new IllegalArgumentException("Failed requirement.");
    try {
      return WindowsCom.withApartment(() -> {
        try (var arena = Arena.ofConfined()) {
          var locator = arena.allocate(ADDRESS);
          WindowsCom.checkResult("CoCreateInstance", (int)Handles.CREATE_INSTANCE.invokeExact(
            WindowsCom.guid(arena, LOCATOR_CLASS), MemorySegment.NULL, CLSCTX_INPROC_SERVER,
            WindowsCom.guid(arena, LOCATOR_INTERFACE), locator));
          return withObject(locator, locatorObject -> withBstr(arena, namespace, namespaceBstr -> {
            var services = arena.allocate(ADDRESS);
            WindowsCom.checkResult("IWbemLocator.ConnectServer", (int)Handles.CONNECT.invokeExact(
              method(locatorObject, 3), locatorObject, namespaceBstr, MemorySegment.NULL, MemorySegment.NULL, MemorySegment.NULL,
              0, MemorySegment.NULL, MemorySegment.NULL, services));
            return withObject(services, servicesObject -> {
              WindowsCom.checkResult("CoSetProxyBlanket", (int)Handles.SET_PROXY_BLANKET.invokeExact(
                servicesObject, RPC_C_AUTHN_WINNT, 0, MemorySegment.NULL, RPC_C_AUTHN_LEVEL_CALL, RPC_C_IMP_LEVEL_IMPERSONATE,
                MemorySegment.NULL, 0));
              return executeQuery(arena, servicesObject, className, properties, timeoutMillis);
            });
          }));
        }
      });
    }
    catch (IOException | TimeoutException | RuntimeException | Error failure) {
      throw failure;
    }
    catch (Throwable failure) {
      throw new IllegalStateException(failure);
    }
  }

  private static @NotNull List<Map<String, @Nullable Object>> executeQuery(@NotNull Arena arena,
                                                                           @NotNull MemorySegment services,
                                                                           @NotNull String className,
                                                                           @NotNull List<String> properties,
                                                                           int timeoutMillis) throws Throwable {
    return withBstr(arena, "WQL", language ->
      withBstr(arena, "SELECT " + String.join(",", properties) + " FROM " + className, query -> {
        var enumerator = arena.allocate(ADDRESS);
        WindowsCom.checkResult("IWbemServices.ExecQuery", (int)Handles.EXEC_QUERY.invokeExact(
          method(services, 20), services, language, query, WBEM_FLAG_RETURN_IMMEDIATELY | WBEM_FLAG_FORWARD_ONLY,
          MemorySegment.NULL, enumerator));
        return withObject(enumerator, enumeratorObject -> {
          var rows = new ArrayList<Map<String, @Nullable Object>>();
          var row = arena.allocate(ADDRESS);
          var returned = arena.allocate(JAVA_INT);
          var propertyNames = new LinkedHashMap<String, MemorySegment>();
          for (var property : properties) {
            propertyNames.put(property, arena.allocateFrom(property, UTF_16LE));
          }
          var variant = arena.allocate(VARIANT_LAYOUT);
          while (true) {
            returned.set(JAVA_INT, 0, 0);
            row.set(ADDRESS, 0, MemorySegment.NULL);
            var result = (int)Handles.NEXT.invokeExact(method(enumeratorObject, 4), enumeratorObject, timeoutMillis, 1, row, returned);
            var count = returned.get(JAVA_INT, 0);
            if (count == 0) {
              WindowsCom.checkResult("IEnumWbemClassObject.Next", result);
              if (result == WBEM_S_TIMEDOUT) throw new TimeoutException("WMI query timed out: " + className);
              break;
            }
            rows.add(withObject(row, rowObject -> {
              WindowsCom.checkResult("IEnumWbemClassObject.Next", result);
              var values = new LinkedHashMap<String, @Nullable Object>();
              for (var property : propertyNames.entrySet()) {
                variant.fill((byte)0);
                try {
                  WindowsCom.checkResult("IWbemClassObject.Get(" + property.getKey() + ")", (int)Handles.GET_PROPERTY.invokeExact(
                    method(rowObject, 4), rowObject, property.getValue(), 0, variant, MemorySegment.NULL, MemorySegment.NULL));
                  values.put(property.getKey(), variantValue(variant));
                }
                finally {
                  WindowsCom.checkResult("VariantClear", (int)Handles.CLEAR_VARIANT.invokeExact(variant));
                }
              }
              return values;
            }));
          }
          return rows;
        });
      }));
  }

  private static @Nullable Object variantValue(@NotNull MemorySegment variant) throws Throwable {
    int type = variant.get(JAVA_SHORT, 0);
    return switch (type) {
      case VT_EMPTY, VT_NULL -> null;
      case VT_I4 -> variant.get(JAVA_INT, 8);
      case VT_BSTR -> {
        var text = variant.get(ADDRESS, 8);
        if (text.address() == 0) yield null;
        var length = (int)Handles.STRING_LENGTH.invokeExact(text);
        yield new String(text.reinterpret((long)length * 2).toArray(JAVA_BYTE), UTF_16LE);
      }
      case VT_BOOL -> variant.get(JAVA_SHORT, 8) != 0;
      default -> throw new IOException("Unsupported WMI VARIANT type: " + type);
    };
  }

  private static <T> T withBstr(@NotNull Arena arena, @NotNull String text, @NotNull NativeAction<T> action) throws Throwable {
    var value = (MemorySegment)Handles.ALLOCATE_STRING.invokeExact(arena.allocateFrom(text, UTF_16LE), text.length());
    if (value.address() == 0) throw new OutOfMemoryError("SysAllocStringLen failed");
    try {
      return action.run(value);
    }
    finally {
      Handles.FREE_STRING.invokeExact(value);
    }
  }

  private static <T> T withObject(@NotNull MemorySegment pointer, @NotNull NativeAction<T> action) throws Throwable {
    var instance = pointer.get(ADDRESS, 0);
    if (instance.address() == 0) throw new IOException("WMI returned a null interface");
    try {
      return action.run(instance);
    }
    finally {
      var _ = (int)Handles.RELEASE.invokeExact(method(instance, 2), instance);
    }
  }

  private static @NotNull MemorySegment method(@NotNull MemorySegment instance, int index) {
    var table = instance.reinterpret(ADDRESS.byteSize()).get(ADDRESS, 0);
    return table.reinterpret((index + 1) * ADDRESS.byteSize()).getAtIndex(ADDRESS, index);
  }

  @FunctionalInterface
  private interface NativeAction<T> {
    T run(MemorySegment instance) throws Throwable;
  }

  private static final class Handles {
    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup OLE32 = WindowsSystemLibraries.lookup("ole32.dll");
    private static final SymbolLookup OLEAUT32 = WindowsSystemLibraries.lookup("oleaut32.dll");
    static final MethodHandle CREATE_INSTANCE = LINKER.downcallHandle(
      OLE32.findOrThrow("CoCreateInstance"), FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, ADDRESS, ADDRESS));
    static final MethodHandle SET_PROXY_BLANKET = LINKER.downcallHandle(
      OLE32.findOrThrow("CoSetProxyBlanket"),
      FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS, JAVA_INT));
    static final MethodHandle ALLOCATE_STRING =
      LINKER.downcallHandle(OLEAUT32.findOrThrow("SysAllocStringLen"), FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT));
    static final MethodHandle FREE_STRING =
      LINKER.downcallHandle(OLEAUT32.findOrThrow("SysFreeString"), FunctionDescriptor.ofVoid(ADDRESS));
    static final MethodHandle STRING_LENGTH =
      LINKER.downcallHandle(OLEAUT32.findOrThrow("SysStringLen"), FunctionDescriptor.of(JAVA_INT, ADDRESS));
    static final MethodHandle CLEAR_VARIANT =
      LINKER.downcallHandle(OLEAUT32.findOrThrow("VariantClear"), FunctionDescriptor.of(JAVA_INT, ADDRESS));
    static final MethodHandle RELEASE = LINKER.downcallHandle(FunctionDescriptor.of(JAVA_INT, ADDRESS));
    static final MethodHandle CONNECT = LINKER.downcallHandle(
      FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, JAVA_INT, ADDRESS, ADDRESS, ADDRESS));
    static final MethodHandle EXEC_QUERY =
      LINKER.downcallHandle(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, JAVA_INT, ADDRESS, ADDRESS));
    static final MethodHandle NEXT = LINKER.downcallHandle(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS, ADDRESS));
    static final MethodHandle GET_PROPERTY =
      LINKER.downcallHandle(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, ADDRESS, ADDRESS, ADDRESS));
  }
}
