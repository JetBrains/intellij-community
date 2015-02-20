package org.jetbrains.debugger;

import com.intellij.util.BitUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.debugger.values.FunctionValue;
import org.jetbrains.debugger.values.Value;

public class ObjectPropertyImpl extends VariableImpl implements ObjectProperty {
  public static final byte WRITABLE = 0x01;
  public static final byte CONFIGURABLE = 0x02;
  public static final byte ENUMERABLE = 0x04;

  private final FunctionValue getter;
  private final FunctionValue setter;

  private final int flags;

  public ObjectPropertyImpl(@NotNull String name,
                            @Nullable Value value,
                            @Nullable FunctionValue getter,
                            @Nullable FunctionValue setter,
                            @Nullable ValueModifier valueModifier,
                            int flags) {
    super(name, value, valueModifier);

    this.getter = getter;
    this.setter = setter;

    this.flags = flags;
  }

  @Nullable
  @Override
  public final FunctionValue getGetter() {
    return getter;
  }

  @Nullable
  @Override
  public final FunctionValue getSetter() {
    return setter;
  }

  @Override
  public final boolean isWritable() {
    return BitUtil.isSet(flags, WRITABLE);
  }

  @Override
  public final boolean isConfigurable() {
    return BitUtil.isSet(flags, CONFIGURABLE);
  }

  @Override
  public final boolean isEnumerable() {
    return BitUtil.isSet(flags, ENUMERABLE);
  }
}