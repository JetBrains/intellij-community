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
package com.intellij.openapi.cvsIntegration;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

/**
 * author: lesya
 */
public class CvsRepository {
  private final String myMethod;
  private final String myUser;
  private final String myHost;
  private final String myRepository;
  private final int myPort;
  private final DateOrRevision myBranch;
  private final String myStringRepresentation;

  private static final Logger LOG = Logger.getInstance(CvsRepository.class);

  public CvsRepository(@NotNull String stringRepresentation,
                       @NotNull String method,
                       String user,
                       @NotNull String host,
                       @NotNull String repository,
                       int port,
                       @NotNull DateOrRevision branch) {
    LOG.assertTrue(port > 0);

    myMethod = method;
    myUser = user;
    myHost = host;
    myRepository = repository;
    myPort = port;
    myBranch = branch;
    myStringRepresentation = stringRepresentation;
  }

  public String getMethod() {
    return myMethod;
  }

  public String getUser() {
    return myUser;
  }

  public String getHost() {
    return myHost;
  }

  public String getRepository() {
    return myRepository;
  }

  public int getPort() {
    return myPort;
  }

  public DateOrRevision getDateOrRevision() {
    return myBranch;
  }

  public String getStringRepresentation() {
    return myStringRepresentation;
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof CvsRepository)) return false;

    final CvsRepository cvsRepository = (CvsRepository)o;

    if (myPort != cvsRepository.myPort) return false;
    if (!myBranch.equals(cvsRepository.myBranch)) return false;
    if (!myHost.equals(cvsRepository.myHost)) return false;
    if (!myMethod.equals(cvsRepository.myMethod)) return false;
    if (!myRepository.equals(cvsRepository.myRepository)) return false;
    if (!myStringRepresentation.equals(cvsRepository.myStringRepresentation)) return false;
    if (myUser != null ? !myUser.equals(cvsRepository.myUser) : cvsRepository.myUser != null) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = myMethod.hashCode();
    result = 29 * result + (myUser != null ? myUser.hashCode() : 0);
    result = 29 * result + myHost.hashCode();
    result = 29 * result + myRepository.hashCode();
    result = 29 * result + myPort;
    result = 29 * result + myBranch.hashCode();
    result = 29 * result + myStringRepresentation.hashCode();
    return result;
  }
}
