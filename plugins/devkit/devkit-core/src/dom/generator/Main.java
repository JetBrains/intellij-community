/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

/*
 * XSD/DTD Model generator tool
 *
 * By Gregory Shrago
 * 2002 - 2006
 */
package org.jetbrains.idea.devkit.dom.generator;

import java.io.File;

/**
 * @author Gregory.Shrago
 * @author Konstantin Bulenkov
 */
public class Main {


  public static void main(String[] argv) throws Exception {
    if (argv.length != 4) {
      System.out.println("Usage: Main <XSD or DTD> <input folder> <output folder> <config xml>");
    }
    String mode = argv[0];
    final ModelLoader loader;
    if (mode.equalsIgnoreCase("xsd")) {
      loader = new XSDModelLoader();
    }
    else if (mode.equalsIgnoreCase("dtd")) {
      loader = new DTDModelLoader();
    }
    else {
      System.out.println("'"+mode+"' format not supported");
      System.exit(-1);
      return;
    }
    final File modelRoot = new File(argv[1]);
    final File outputRoot = new File(argv[2]);
    final File configXml = new File(argv[3]);

    outputRoot.mkdirs();
    final ModelGen modelGen = new ModelGen(loader);
    modelGen.loadConfig(configXml);
    modelGen.perform(outputRoot, modelRoot);
  }

}
