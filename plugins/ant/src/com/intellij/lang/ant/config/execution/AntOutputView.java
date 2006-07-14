package com.intellij.lang.ant.config.execution;

import javax.swing.*;

public interface AntOutputView {
  Object addMessage(AntMessage message);
  void addJavacMessage(AntMessage message, String url);
  void addException(AntMessage exception, boolean showFullTrace);
  void startBuild(AntMessage message);
  void startTarget(AntMessage message);
  void startTask(AntMessage message);
  void finishBuild(String messageText);
  void finishTarget();
  void finishTask();

  Object getData(String dataId);

  void buildFailed(AntMessage message);

  JComponent getComponent();
}

