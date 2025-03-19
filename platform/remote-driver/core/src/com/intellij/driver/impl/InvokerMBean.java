package com.intellij.driver.impl;

import com.intellij.driver.model.ProductVersion;
import com.intellij.driver.model.transport.Ref;
import com.intellij.driver.model.transport.RemoteCall;
import com.intellij.driver.model.transport.RemoteCallResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("unused")
public interface InvokerMBean {
  ProductVersion getProductVersion();

  boolean isApplicationInitialized();

  void exit();

  RemoteCallResult invoke(RemoteCall call);

  int newSession();

  int newSession(int id);

  void cleanup(int sessionId);

  String takeScreenshot(@Nullable String outFolder);

  @NotNull Ref putAdhocReference(@NotNull Object item);
}
