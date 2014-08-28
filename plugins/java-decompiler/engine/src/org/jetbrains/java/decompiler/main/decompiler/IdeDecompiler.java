/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.java.decompiler.main.decompiler;

import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.Fernflower;
import org.jetbrains.java.decompiler.main.extern.IBytecodeProvider;
import org.jetbrains.java.decompiler.main.extern.IDecompilatSaver;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;


public class IdeDecompiler {

  private Fernflower fernflower;

  public IdeDecompiler(IBytecodeProvider provider,
                       IDecompilatSaver saver, IFernflowerLogger logger,
                       HashMap<String, Object> propertiesCustom) {

    fernflower = new Fernflower(provider, saver, propertiesCustom);

    DecompilerContext.setLogger(logger);
  }

  public void addSpace(File file, boolean isOwn) throws IOException {
    fernflower.getStructcontext().addSpace(file, isOwn);
  }

  public void decompileContext() {
    try {
      fernflower.decompileContext();
    }
    finally {
      fernflower.clearContext();
    }
  }
}
