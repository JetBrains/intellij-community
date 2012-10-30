package org.jetbrains.jps.android.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.android.model.JpsAndroidDexCompilerConfiguration;
import org.jetbrains.jps.model.JpsElementChildRole;
import org.jetbrains.jps.model.ex.JpsElementBase;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;

/**
 * @author Eugene.Kudelevsky
 */
public class JpsAndroidDexCompilerConfigurationImpl extends JpsElementBase<JpsAndroidDexCompilerConfigurationImpl>
  implements JpsAndroidDexCompilerConfiguration {

  public static final JpsElementChildRole<JpsAndroidDexCompilerConfiguration> ROLE =
    JpsElementChildRoleBase.create("android dex compiler configuration");

  private final MyState myState = new MyState();

  public JpsAndroidDexCompilerConfigurationImpl() {
  }

  public JpsAndroidDexCompilerConfigurationImpl(@NotNull MyState state) {
    myState.MAX_HEAP_SIZE = state.MAX_HEAP_SIZE;
    myState.OPTIMIZE = state.OPTIMIZE;
    myState.VM_OPTIONS = state.VM_OPTIONS;
  }

  @Override
  public String getVmOptions() {
    return myState.VM_OPTIONS;
  }

  @Override
  public void setVmOptions(String value) {
    if (!myState.VM_OPTIONS.equals(value)) {
      myState.VM_OPTIONS = value;
      fireElementChanged();
    }
  }

  @Override
  public int getMaxHeapSize() {
    return myState.MAX_HEAP_SIZE;
  }

  @Override
  public void setMaxHeapSize(int value) {
    if (myState.MAX_HEAP_SIZE != value) {
      myState.MAX_HEAP_SIZE = value;
      fireElementChanged();
    }
  }

  @Override
  public boolean isOptimize() {
    return myState.OPTIMIZE;
  }

  @Override
  public void setOptimize(boolean value) {
    if (myState.OPTIMIZE != value) {
      myState.OPTIMIZE = value;
      fireElementChanged();
    }
  }

  @NotNull
  @Override
  public JpsAndroidDexCompilerConfigurationImpl createCopy() {
    return new JpsAndroidDexCompilerConfigurationImpl(myState);
  }

  @Override
  public void applyChanges(@NotNull JpsAndroidDexCompilerConfigurationImpl modified) {
    setVmOptions(modified.getVmOptions());
    setMaxHeapSize(modified.getMaxHeapSize());
    setOptimize(modified.isOptimize());
  }

  @NotNull
  public MyState getState() {
    return myState;
  }

  public static class MyState {
    public String VM_OPTIONS = "";
    public int MAX_HEAP_SIZE = 1024;
    public boolean OPTIMIZE = true;
  }
}
