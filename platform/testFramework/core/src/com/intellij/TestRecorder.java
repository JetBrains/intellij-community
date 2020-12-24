package com.intellij;

import com.intellij.idea.RecordExecution;

/**
 * @author yole
 */
public interface TestRecorder {
  void beginRecording(Class testClass, RecordExecution recordParameters);
  void endRecording();
}
