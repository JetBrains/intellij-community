/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.cvsSupport2.connections.pserver;

import com.intellij.cvsSupport2.connections.login.CvsLoginWorker;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;


/**
 * author: lesya
 */
public abstract class PServerLoginProvider {
  private static PServerLoginProvider myInstance = new PServerLoginProviderImpl();

  public static PServerLoginProvider getInstance() {
    return myInstance;
  }

  public static void registerPasswordProvider(PServerLoginProvider passProvider){
    myInstance = passProvider;
  }

  @Nullable
  public abstract String getScrambledPasswordForCvsRoot(String cvsroot);

  public abstract CvsLoginWorker getLoginWorker(final Project project, final PServerCvsSettings pServerCvsSettings);
}
