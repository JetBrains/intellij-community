package com.intellij.remoteServer.agent.impl;

import java.lang.reflect.InvocationHandler;

/**
 * @author michael.golubev
 */
public interface ChildWrapperCreator {

  InvocationHandler createWrapperInvocationHandler(Object child);
}
