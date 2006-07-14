package com.intellij.lang.ant.config;

public interface AntBuildListener {
  int FINISHED_SUCCESSFULLY = 0;
  int ABORTED = 1;
  int FAILED_TO_RUN = 2;

  AntBuildListener NULL = new AntBuildListener() {
    public void buildFinished(int state, int errorCount) { }
  };

  void buildFinished(int state, int errorCount);
}
