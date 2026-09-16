package com.intellij.util.system;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.nio.charset.StandardCharsets.UTF_16LE;

@ApiStatus.Internal
public final class WindowsShellOperations {
  private WindowsShellOperations() { }

  public static void openDirectory(@NotNull Path directory) throws IOException {
    try {
      WindowsCom.withApartment(() -> {
        try (var arena = Arena.ofConfined()) {
          var result = (MemorySegment)Handles.EXECUTE.invokeExact(
            MemorySegment.NULL, arena.allocateFrom("explore", UTF_16LE), arena.allocateFrom(directory.toString(), UTF_16LE),
            MemorySegment.NULL, MemorySegment.NULL, 1);
          if (result.address() <= 32) throw new IOException("ShellExecuteW(" + directory + ") failed with code " + result.address());
        }
        return null;
      });
    }
    catch (IOException | RuntimeException | Error failure) {
      throw failure;
    }
    catch (Throwable failure) {
      throw new IllegalStateException(failure);
    }
  }

  public static void selectFile(@NotNull Path file) throws IOException {
    try {
      WindowsCom.withApartment(() -> {
        try (var arena = Arena.ofConfined()) {
          var item = (MemorySegment)Handles.CREATE_ITEM.invokeExact(arena.allocateFrom(file.toString(), UTF_16LE));
          if (item.address() == 0) throw new IOException("ILCreateFromPathW(" + file + ") failed");
          try {
            WindowsCom.checkResult("SHOpenFolderAndSelectItems(" + file + ")",
                                   (int)Handles.SELECT_ITEM.invokeExact(item, 0, MemorySegment.NULL, 0));
          }
          finally {
            Handles.FREE_ITEM.invokeExact(item);
          }
        }
        return null;
      });
    }
    catch (IOException | RuntimeException | Error failure) {
      throw failure;
    }
    catch (Throwable failure) {
      throw new IllegalStateException(failure);
    }
  }

  private static final class Handles {
    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup SHELL32 = WindowsSystemLibraries.lookup("shell32.dll");
    static final MethodHandle EXECUTE = LINKER.downcallHandle(
      SHELL32.findOrThrow("ShellExecuteW"), FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, JAVA_INT));
    static final MethodHandle CREATE_ITEM =
      LINKER.downcallHandle(SHELL32.findOrThrow("ILCreateFromPathW"), FunctionDescriptor.of(ADDRESS, ADDRESS));
    static final MethodHandle FREE_ITEM = LINKER.downcallHandle(SHELL32.findOrThrow("ILFree"), FunctionDescriptor.ofVoid(ADDRESS));
    static final MethodHandle SELECT_ITEM = LINKER.downcallHandle(
      SHELL32.findOrThrow("SHOpenFolderAndSelectItems"), FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT));
  }
}
