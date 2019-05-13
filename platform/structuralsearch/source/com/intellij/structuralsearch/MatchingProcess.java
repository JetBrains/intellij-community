package com.intellij.structuralsearch;

/**
 * Interface of running matching process
 */
public interface MatchingProcess {
  void stop();
  void pause();
  void resume();

  boolean isSuspended();
  boolean isEnded();
}
