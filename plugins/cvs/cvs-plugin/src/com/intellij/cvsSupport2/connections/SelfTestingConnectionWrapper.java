/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.cvsSupport2.connections;

import com.intellij.cvsSupport2.javacvsImpl.io.ReadWriteStatistics;
import org.netbeans.lib.cvsclient.ICvsCommandStopper;
import org.netbeans.lib.cvsclient.connection.AuthenticationException;
import org.netbeans.lib.cvsclient.connection.IConnection;

/**
 * author: lesya
 */
public class SelfTestingConnectionWrapper extends ConnectionWrapper implements SelfTestingConnection{
  public SelfTestingConnectionWrapper(IConnection sourceConnection, ReadWriteStatistics statistics, ICvsCommandStopper cvsCommandStopper) {
    super(sourceConnection, statistics, cvsCommandStopper);
  }

  @Override
  public void test(ICvsCommandStopper stopper) throws AuthenticationException {
    ((SelfTestingConnection)mySourceConnection).test(stopper);
  }
}
