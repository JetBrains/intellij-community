// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.struct.attr;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jetbrains.java.decompiler.struct.consts.ConstantPool;
import org.jetbrains.java.decompiler.util.DataInputFullStream;

public class StructModuleAttribute extends StructGeneralAttribute {
  public String moduleName;
  public int moduleFlags;
  public String moduleVersion;

  public List<RequiresEntry> requires;
  public List<ExportsEntry> exports;
  public List<OpensEntry> opens;
  public List<String> uses;
  public List<ProvidesEntry> provides;

  @Override
  public void initContent(DataInputFullStream data, ConstantPool pool) throws IOException {
    int moduleNameIndex = data.readUnsignedShort();

    this.moduleName = pool.getPrimitiveConstant(moduleNameIndex).getString();
    this.moduleFlags = data.readUnsignedShort();

    int moduleVersionIndex = data.readUnsignedShort();
    if (moduleVersionIndex != 0) {
      moduleVersion = pool.getPrimitiveConstant(moduleVersionIndex).getString();
    }

    this.requires = readRequires(data, pool);
    this.exports = readExports(data, pool);
    this.opens = readOpens(data, pool);
    this.uses = readUses(data, pool);
    this.provides = readProvides(data, pool);
  }

  public List<RequiresEntry> readRequires(DataInputFullStream data, ConstantPool pool) throws IOException {
    int requiresCount = data.readUnsignedShort();

    if (requiresCount <= 0) {
      return Collections.emptyList();
    }

    List<RequiresEntry> requires = new ArrayList<>(requiresCount);
    for (int i = 0; i < requiresCount; i++) {
      int moduleNameIndex = data.readUnsignedShort();
      int moduleFlags = data.readUnsignedShort();
      int versionIndex = data.readUnsignedShort();

      String moduleName = pool.getPrimitiveConstant(moduleNameIndex).getString();
      String version = versionIndex == 0 ? null : pool.getPrimitiveConstant(versionIndex).getString();

      requires.add(new RequiresEntry(moduleName, moduleFlags, version));
    }

    return requires;
  }

  private List<ExportsEntry> readExports(DataInputFullStream data, ConstantPool pool) throws IOException {
    int exportsCount = data.readUnsignedShort();

    if (exportsCount <= 0) {
      return Collections.emptyList();
    }

    List<ExportsEntry> exports = new ArrayList<>(exportsCount);

    for (int i = 0; i < exportsCount; i++) {
      int packageNameIndex = data.readUnsignedShort();
      int exportsFlags = data.readUnsignedShort();
      int exportsToCount = data.readUnsignedShort();

      List<String> exportsToModules;
      if (exportsToCount > 0) {
        exportsToModules = new ArrayList<>(exportsToCount);

        for (int j = 0; j < exportsToCount; j++) {
          int moduleNameIndex = data.readUnsignedShort();
          String moduleName = pool.getPrimitiveConstant(moduleNameIndex).getString();

          exportsToModules.add(moduleName);
        }
      } else {
        exportsToModules = Collections.emptyList();
      }

      String packageName = pool.getPrimitiveConstant(packageNameIndex).getString();

      exports.add(new ExportsEntry(packageName, exportsFlags, exportsToModules));
    }

    return exports;
  }

  private List<OpensEntry> readOpens(DataInputFullStream data, ConstantPool pool) throws IOException {
    int opensCount = data.readUnsignedShort();

    if (opensCount <= 0) {
      return Collections.emptyList();
    }

    List<OpensEntry> opens = new ArrayList<>(opensCount);

    for (int i = 0; i < opensCount; i++) {
      int packageNameIndex = data.readUnsignedShort();
      int opensFlags = data.readUnsignedShort();
      int opensToCount = data.readUnsignedShort();

      List<String> opensToModules;
      if (opensToCount > 0) {
        opensToModules = new ArrayList<>(opensToCount);

        for (int j = 0; j < opensToCount; j++) {
          int moduleNameIndex = data.readUnsignedShort();
          String moduleName = pool.getPrimitiveConstant(moduleNameIndex).getString();

          opensToModules.add(moduleName);
        }
      } else {
        opensToModules = Collections.emptyList();
      }

      String packageName = pool.getPrimitiveConstant(packageNameIndex).getString();

      opens.add(new OpensEntry(packageName, opensFlags, opensToModules));
    }

    return opens;
  }

  private List<String> readUses(DataInputFullStream data, ConstantPool pool) throws IOException {
    int usesCount = data.readUnsignedShort();
    if (usesCount <= 0) {
      return Collections.emptyList();
    }

    List<String> uses = new ArrayList<>(usesCount);
    for (int i = 0; i < usesCount; i++) {
      int classNameIndex = data.readUnsignedShort();
      String className = pool.getPrimitiveConstant(classNameIndex).getString();

      uses.add(className);
    }

    return uses;
  }

  private List<ProvidesEntry> readProvides(DataInputFullStream data, ConstantPool pool) throws IOException {
    int providesCount = data.readUnsignedShort();
    if (providesCount <= 0) {
      return Collections.emptyList();
    }

    List<ProvidesEntry> provides = new ArrayList<>(providesCount);
    for (int i = 0; i < providesCount; i++) {
      int interfaceNameIndex = data.readUnsignedShort();
      String interfaceName = pool.getPrimitiveConstant(interfaceNameIndex).getString();

      // Always nonzero
      int providesWithCount = data.readUnsignedShort();
      List<String> implementationNames = new ArrayList<>(providesWithCount);

      for (int j = 0; j < providesWithCount; j++) {
        int classNameIndex = data.readUnsignedShort();
        String className = pool.getPrimitiveConstant(classNameIndex).getString();

        implementationNames.add(className);
      }

      provides.add(new ProvidesEntry(interfaceName, implementationNames));
    }

    return provides;
  }

  public static final class RequiresEntry {
    public String moduleName;
    public int moduleFlags;
    public String moduleVersion;

    public RequiresEntry(String moduleName, int moduleFlags, String moduleVersion) {
      this.moduleName = moduleName;
      this.moduleFlags = moduleFlags;
      this.moduleVersion = moduleVersion;
    }
  }

  public static final class ExportsEntry {
    public String packageName;
    public int exportsFlags;
    public List<String> exportToModules;

    public ExportsEntry(String packageName, int exportsFlags, List<String> exportToModules) {
      this.packageName = packageName;
      this.exportsFlags = exportsFlags;
      this.exportToModules = exportToModules;
    }
  }

  public static final class OpensEntry {
    public String packageName;
    public int opensFlags;
    public List<String> opensToModules;

    public OpensEntry(String packageName, int exportsFlags, List<String> exportToModules) {
      this.packageName = packageName;
      this.opensFlags = exportsFlags;
      this.opensToModules = exportToModules;
    }
  }

  public static final class ProvidesEntry {
    public String interfaceName;
    public List<String> implementationNames;

    public ProvidesEntry(String interfaceName, List<String> implementationNames) {
      this.interfaceName = interfaceName;
      this.implementationNames = implementationNames;
    }
  }

}
