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

import com.intellij.cvsSupport2.connections.pserver.PServerLoginProvider;

public class CvsRootDataBuilder implements CvsRootSettingsBuilder<CvsRootData>{

  public static CvsRootData createSettingsOn(String cvsRoot, boolean check) {
    return new RootFormatter<>(new CvsRootDataBuilder()).createConfiguration(cvsRoot, check);
  }


  public CvsRootData createSettings(final CvsMethod method, final String cvsRootAsString) {
    final CvsRootData result = new CvsRootData(cvsRootAsString);
    result.METHOD = method;
    return result;
  }

  public String getPServerPassword(final String cvsRoot) {
    return PServerLoginProvider.getInstance().getScrambledPasswordForCvsRoot(cvsRoot);
  }
}
