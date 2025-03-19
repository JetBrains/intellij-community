// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler.util;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.main.extern.IVariableNameProvider;
import org.jetbrains.java.decompiler.main.extern.IVariableNamingFactory;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarVersion;
import org.jetbrains.java.decompiler.struct.StructMethod;
import org.jetbrains.java.decompiler.struct.gen.MethodDescriptor;
import org.jetbrains.java.decompiler.struct.gen.VarType;

import java.util.*;
import java.util.Map.Entry;
import java.util.regex.Pattern;

public class JADNameProvider implements IVariableNameProvider {
  private HashMap<String, Holder> last;
  private HashMap<String, String> remap;
  private final HashMap<Integer, String> parameters = new HashMap<>();
  private final StructMethod method;
  private final boolean renameParameters;
  private static final Pattern CAPS_START = Pattern.compile("^[A-Z]");
  private static final Pattern ARRAY = Pattern.compile("(\\[|\\.\\.\\.)");

  public JADNameProvider(boolean renameParameters, StructMethod wrapper) {
    last = new HashMap<>();
    last.put("int",     new Holder(0, true,  "i", "j", "k", "l"));
    last.put("byte",    new Holder(0, false, "b"       ));
    last.put("char",    new Holder(0, false, "c"       ));
    last.put("short",   new Holder(1, false, "short"   ));
    last.put("boolean", new Holder(0, true,  "flag"    ));
    last.put("double",  new Holder(0, false, "d"       ));
    last.put("float",   new Holder(0, true,  "f"       ));
    last.put("File",    new Holder(1, true,  "file"    ));
    last.put("String",  new Holder(0, true,  "s"       ));
    last.put("Class",   new Holder(0, true,  "oclass"  ));
    last.put("Long",    new Holder(0, true,  "olong"   ));
    last.put("Byte",    new Holder(0, true,  "obyte"   ));
    last.put("Short",   new Holder(0, true,  "oshort"  ));
    last.put("Boolean", new Holder(0, true,  "obool"   ));
    last.put("Package", new Holder(0, true,  "opackage"));
    last.put("Enum",    new Holder(0, true,  "oenum"   ));

    remap = new HashMap<>();
    remap.put("long", "int");

    this.method = wrapper;
    this.renameParameters = renameParameters;
  }

  @Override
  public synchronized void addParentContext(IVariableNameProvider iparent) {
    JADNameProvider parent = (JADNameProvider) iparent;
    HashMap<String, Holder> temp = new HashMap<>();
    for (Entry<String, Holder> e : parent.last.entrySet()) {
      temp.put(e.getKey(), e.getValue().copy());
    }
    this.last = temp;
    this.remap = new HashMap<>(parent.remap);
  }

  private static class Holder {
    public int id;
    public boolean skip_zero;
    public final List<String> names = new ArrayList<>();

    private Holder(int t1, boolean skip_zero, String... names) {
      if (names.length == 0) {
        throw new IllegalArgumentException("names must not be empty");
      }
      this.id = t1;
      this.skip_zero = skip_zero;
      Collections.addAll(this.names, names);
    }

    private Holder(int t1, boolean skip_zero, List<String> names) {
      if (names.isEmpty()) {
        throw new IllegalArgumentException("names must not be empty");
      }
      this.id = t1;
      this.skip_zero = skip_zero;
      this.names.addAll(names);
    }

    @Override
    public String toString() {
      return "Holder[" + id + ", " + skip_zero + ", " + String.join(", ", names) + "]";
    }

    public Holder copy() {
        return new Holder(this.id, this.skip_zero, new ArrayList<>(this.names));
    }
  }

  @Override
  public Map<VarVersion,String> rename(Map<VarVersion, String> entries) {
    int params = 0;
    if ((this.method.getAccessFlags() & CodeConstants.ACC_STATIC) != CodeConstants.ACC_STATIC) {
      params++;
    }

    MethodDescriptor md = MethodDescriptor.parseDescriptor(this.method.getDescriptor());
    for (VarType param : md.params) {
      params += param.getStackSize();
    }

    List<VarVersion> keys = new ArrayList<>(entries.keySet());
    Collections.sort(keys);

    Map<VarVersion, String> result = new LinkedHashMap<>();
    for (VarVersion ver : keys) {
      String type = cleanType(entries.get(ver));
      if ("this".equals(type)) {
        continue;
      }
      if (ver.var >= params) {
        result.put(ver, getNewName(type));
      } else if (renameParameters) {
        result.put(ver, this.parameters.computeIfAbsent(ver.var, k -> getNewName(type)));
      }
    }
    return result;
  }

  private static String cleanType(String type) {
     if (type.indexOf('<') != -1) {
        type = type.substring(0, type.indexOf('<'));
     }
     if (type.indexOf('.') != -1) {
        type = type.substring(type.lastIndexOf('.') + 1);
     }
     return type;
  }

  protected synchronized String getNewName(String type) {
    String index = null;
    String findtype = type;

    while (findtype.contains("[][]")) {
      findtype = findtype.replace("[][]", "[]");
    }
    if (last.containsKey(findtype)) {
      index = findtype;
    }
    else if (last.containsKey(findtype.toLowerCase(Locale.ENGLISH))) {
      index = findtype.toLowerCase(Locale.ENGLISH);
    }
    else if (remap.containsKey(type)) {
      index = remap.get(type);
    }

    if ((index == null || index.isEmpty()) && (CAPS_START.matcher(type).find() || ARRAY.matcher(type).find())) { // replace multi things with arrays.
      type = type.replace("...", "[]");

      while (type.contains("[][]")) {
        type = type.replace("[][]", "[]");
      }

      String name = type.toLowerCase(Locale.ENGLISH);
      // Strip single dots that might happen because of inner class references
      name = name.replace(".", "");
      boolean skip_zero = false;

      if (type.contains("[")) {
        skip_zero = true;
        name = "a" + name.replace("[]", "").replace("...", "");
      }

      last.put(type.toLowerCase(Locale.ENGLISH), new Holder(0, skip_zero, name));
      index = type.toLowerCase(Locale.ENGLISH);
    }

    if (index == null || index.isEmpty()) {
      return type.toLowerCase(Locale.ENGLISH);
    }

    Holder holder = last.get(index);
    int id = holder.id;
    List<String> names = holder.names;

    int amount = names.size();

    String name;
    if (amount == 1) {
      name = names.get(0) + (id == 0 && holder.skip_zero ? "" : id);
    }
    else {
      int num = id / amount;
      name = names.get(id % amount) + (id < amount && holder.skip_zero ? "" : num);
    }

    holder.id++;
    return name;
  }

  @Override
  public String renameParameter(int flags, String type, String name, int index) {
     if (!this.renameParameters)
         return IVariableNameProvider.super.renameParameter(flags, type, name, index);
     return this.parameters.computeIfAbsent(index, k -> getNewName(cleanType(type)));
  }

  public static class JADNameProviderFactory implements IVariableNamingFactory {
    private final boolean renameParameters;
    public JADNameProviderFactory(boolean renameParameters) {
        this.renameParameters = renameParameters;
    }
    @Override
    public IVariableNameProvider createFactory(StructMethod method) {
      return new JADNameProvider(renameParameters, method);
    }
  }
}
