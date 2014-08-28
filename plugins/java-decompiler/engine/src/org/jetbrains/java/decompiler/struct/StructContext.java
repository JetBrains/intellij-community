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
package org.jetbrains.java.decompiler.struct;

import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.extern.IDecompilatSaver;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;
import org.jetbrains.java.decompiler.struct.lazy.LazyLoader;

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;


public class StructContext {

  // *****************************************************************************
  // private fields
  // *****************************************************************************

  private LazyLoader loader;

  private HashMap<String, StructClass> classes = new HashMap<String, StructClass>();

  private HashMap<String, ContextUnit> units = new HashMap<String, ContextUnit>();

  private ContextUnit defaultUnit;

  private IDecompilatSaver saver;

  private IDecompiledData decdata;

  public StructContext(IDecompilatSaver saver, IDecompiledData decdata, LazyLoader loader) {

    this.saver = saver;
    this.decdata = decdata;
    this.loader = loader;

    defaultUnit = new ContextUnit(ContextUnit.TYPE_FOLDER, null, "", true, saver, decdata);
    units.put("", defaultUnit);
  }

  // *****************************************************************************
  // public methods
  // *****************************************************************************

  public StructClass getClass(String name) {
    return classes.get(name);
  }

  public void reloadContext() throws IOException {

    for (ContextUnit unit : units.values()) {

      for (StructClass cl : unit.getClasses()) {
        classes.remove(cl.qualifiedName);
      }

      unit.reload(loader);

      // adjust lobal class collection
      for (StructClass cl : unit.getClasses()) {
        classes.put(cl.qualifiedName, cl);
      }
    }
  }

  public void saveContext() {

    for (ContextUnit unit : units.values()) {
      if (unit.isOwn()) {
        unit.save();
      }
    }
  }

  public void addSpace(File file, boolean isOwn) throws IOException {
    addSpace("", file, isOwn);
  }

  private void addSpace(String path, File file, boolean isOwn) throws IOException {

    if (file.isDirectory()) {

      File[] files = file.listFiles();
      path += "/" + (path.length() == 0 ? "" : file.getName());

      for (int i = files.length - 1; i >= 0; i--) {
        addSpace(path, files[i], isOwn);
      }
    }
    else {

      String filename = file.getName();

      boolean isArchive = false;

      try {
        if (filename.endsWith(".jar")) {
          addArchive(path, file, ContextUnit.TYPE_JAR, isOwn);
          isArchive = true;
        }
        else if (filename.endsWith(".zip")) {
          addArchive(path, file, ContextUnit.TYPE_ZIP, isOwn);
          isArchive = true;
        }
      }
      catch (IOException ex) {
        DecompilerContext.getLogger()
          .writeMessage("Invalid archive file: " + (path.length() > 0 ? path + "/" : "") + filename, IFernflowerLogger.ERROR);
      }

      if (!isArchive) {
        ContextUnit unit = units.get(path);
        if (unit == null) {
          unit = new ContextUnit(ContextUnit.TYPE_FOLDER, null, path, isOwn, saver, decdata);
          units.put(path, unit);
        }

        boolean isClass = false;

        if (filename.endsWith(".class")) {
          try {
            StructClass cl = new StructClass(loader.getClassStream(file.getAbsolutePath(), null), isOwn, loader);

            classes.put(cl.qualifiedName, cl);
            unit.addClass(cl, filename);
            loader.addClassLink(cl.qualifiedName, new LazyLoader.Link(LazyLoader.Link.CLASS, file.getAbsolutePath(), null));

            isClass = true;
          }
          catch (IOException ex) {
            DecompilerContext.getLogger()
              .writeMessage("Invalid class file: " + (path.length() > 0 ? path + "/" : "") + filename, IFernflowerLogger.ERROR);
          }
        }

        if (!isClass) {
          unit.addOtherEntry(file.getAbsolutePath(), filename);
        }
      }
    }
  }


  private void addArchive(String path, File file, int type, boolean isOwn) throws IOException {

    ZipFile archive;

    if (type == ContextUnit.TYPE_JAR) {  // jar
      archive = new JarFile(file);
    }
    else {  // zip
      archive = new ZipFile(file);
    }

    Enumeration<? extends ZipEntry> en = archive.entries();
    while (en.hasMoreElements()) {
      ZipEntry entr = en.nextElement();

      ContextUnit unit = units.get(path + "/" + file.getName());
      if (unit == null) {
        unit = new ContextUnit(type, path, file.getName(), isOwn, saver, decdata);
        if (type == ContextUnit.TYPE_JAR) {
          unit.setManifest(((JarFile)archive).getManifest());
        }
        units.put(path + "/" + file.getName(), unit);
      }

      String name = entr.getName();
      if (!entr.isDirectory()) {
        if (name.endsWith(".class")) {
          StructClass cl = new StructClass(archive.getInputStream(entr), isOwn, loader);
          classes.put(cl.qualifiedName, cl);

          unit.addClass(cl, name);

          if (loader != null) {
            loader.addClassLink(cl.qualifiedName, new LazyLoader.Link(LazyLoader.Link.ENTRY, file.getAbsolutePath(), name));
          }
        }
        else {
          unit.addOtherEntry(file.getAbsolutePath(), name);
        }
      }
      else if (entr.isDirectory()) {
        unit.addDirEntry(name);
      }
    }
  }

  // *****************************************************************************
  // getter and setter methods
  // *****************************************************************************

  public HashMap<String, StructClass> getClasses() {
    return classes;
  }
}
