// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler.util;

import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger.Severity;
import org.jetbrains.java.decompiler.struct.StructContext;

import java.io.File;
import java.io.IOException;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class ClasspathScanner {

    public static void addAllClasspath(StructContext ctx) {
      Set<String> found = new HashSet<>();
      String[] props = { System.getProperty("java.class.path"), System.getProperty("sun.boot.class.path") };
      for (String prop : props) {
        if (prop == null)
          continue;

        for (final String path : prop.split(File.pathSeparator)) {
          File file = new File(path);
          if (found.contains(file.getAbsolutePath()))
            continue;

          if (file.exists() && (file.getName().endsWith(".class") || file.getName().endsWith(".jar"))) {
            DecompilerContext.getLogger().writeMessage("Adding File to context from classpath: " + file, Severity.INFO);
            ctx.addSpace(file, false);
            found.add(file.getAbsolutePath());
          }
        }
      }

      addAllModulePath(ctx);
    }

    private static void addAllModulePath(StructContext ctx) {
      for (ModuleReference module : ModuleFinder.ofSystem().findAll()) {
        String name = module.descriptor().name();
        try {
          ModuleReader reader = module.open();
          DecompilerContext.getLogger().writeMessage("Reading Module: " + name, Severity.INFO);
          reader.list().forEach(cls -> {
            if (!cls.endsWith(".class") || cls.contains("module-info.class"))
              return;

            DecompilerContext.getLogger().writeMessage("  " + cls, Severity.INFO);
            try {
              Optional<ByteBuffer> bb = reader.read(cls);
              if (!bb.isPresent()) {
                DecompilerContext.getLogger().writeMessage("    Error Reading Class: " + cls, Severity.ERROR);
                return;
              }

              byte[] data;
              if (bb.get().hasArray()) {
                data = bb.get().array();
              } else {
                data = new byte[bb.get().remaining()];
                bb.get().get(data);
              }
              ctx.addData(name, cls, data, false);
            } catch (IOException e) {
              DecompilerContext.getLogger().writeMessage("    Error Reading Class: " + cls, e);
            }
          });
          reader.close();
        } catch (IOException e) {
          DecompilerContext.getLogger().writeMessage("Error loading module " + name, e);
        }
      }
    }
}
