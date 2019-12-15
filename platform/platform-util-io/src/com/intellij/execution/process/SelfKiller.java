// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process;

/**
 * Marker interface that represents a process that kills itself, for example a remote process, that can't be killed by the local OS.
 *
 * The process that can't be killed by local OS should implement this interface for OSProcessHandler to work correctly.
 * The process implementing this interface is supposed to be destroyed with Process.destroy() method.
 *
 * Also implementations of ProcessHandler should take in account that process can implement this interface
 */
public interface SelfKiller {}
