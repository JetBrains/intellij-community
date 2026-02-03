// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remote;

import com.intellij.execution.process.PtyBasedProcess;
import com.intellij.execution.process.SelfKiller;

public abstract class RemoteSshProcess extends RemoteProcess implements SelfKiller, Tunnelable, PtyBasedProcess { }
