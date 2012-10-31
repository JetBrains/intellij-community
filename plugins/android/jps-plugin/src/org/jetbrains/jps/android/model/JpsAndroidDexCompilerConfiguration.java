package org.jetbrains.jps.android.model;

import org.jetbrains.jps.model.JpsElement;

/**
 * @author Eugene.Kudelevsky
 */
public interface JpsAndroidDexCompilerConfiguration extends JpsElement {
  String getVmOptions();

  void setVmOptions(String value);

  int getMaxHeapSize();

  void setMaxHeapSize(int value);

  boolean isOptimize();

  void setOptimize(boolean value);
}
