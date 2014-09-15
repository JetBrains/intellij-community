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
package org.jetbrains.java.decompiler.modules.renamer;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.main.extern.IIdentifierRenamer;
import org.jetbrains.java.decompiler.struct.StructClass;
import org.jetbrains.java.decompiler.struct.StructContext;
import org.jetbrains.java.decompiler.struct.StructField;
import org.jetbrains.java.decompiler.struct.StructMethod;
import org.jetbrains.java.decompiler.struct.gen.FieldDescriptor;
import org.jetbrains.java.decompiler.struct.gen.MethodDescriptor;
import org.jetbrains.java.decompiler.struct.gen.VarType;
import org.jetbrains.java.decompiler.util.VBStyleCollection;

import java.io.IOException;
import java.util.*;

public class IdentifierConverter {

  private StructContext context;

  private IIdentifierRenamer helper;

  private PoolInterceptor interceptor;

  private List<ClassWrapperNode> rootClasses = new ArrayList<ClassWrapperNode>();

  private List<ClassWrapperNode> rootInterfaces = new ArrayList<ClassWrapperNode>();

  private HashMap<String, HashMap<String, String>> interfaceNameMaps = new HashMap<String, HashMap<String, String>>();

  public void rename(StructContext context) {

    try {
      this.context = context;

      String user_class = (String)DecompilerContext.getProperty(IFernflowerPreferences.USER_RENAMER_CLASS);
      if (user_class != null) {
        try {
          helper = (IIdentifierRenamer)IdentifierConverter.class.getClassLoader().loadClass(user_class).newInstance();
        }
        catch (Exception ex) {
          // ignore errors
        }
      }

      if (helper == null) {
        helper = new ConverterHelper();
      }

      interceptor = new PoolInterceptor(helper);

      buildInheritanceTree();

      renameAllClasses();

      renameInterfaces();

      renameClasses();

      DecompilerContext.setPoolInterceptor(interceptor);
      context.reloadContext();
    }
    catch (IOException ex) {
      throw new RuntimeException("Renaming failed!");
    }
  }

  private void renameClasses() {

    List<ClassWrapperNode> lstClasses = getReversePostOrderListIterative(rootClasses);

    HashMap<String, HashMap<String, String>> classNameMaps = new HashMap<String, HashMap<String, String>>();

    for (ClassWrapperNode node : lstClasses) {

      StructClass cl = node.getClassStruct();
      HashMap<String, String> names = new HashMap<String, String>();

      // merge informations on super class
      if (cl.superClass != null) {
        HashMap<String, String> mapClass = classNameMaps.get(cl.superClass.getString());
        if (mapClass != null) {
          names.putAll(mapClass);
        }
      }

      // merge informations on interfaces
      for (String intrName : cl.getInterfaceNames()) {
        HashMap<String, String> mapInt = interfaceNameMaps.get(intrName);
        if (mapInt != null) {
          names.putAll(mapInt);
        }
        else {
          StructClass clintr = context.getClass(intrName);
          if (clintr != null) {
            names.putAll(processExternalInterface(clintr));
          }
        }
      }

      renameClassIdentifiers(cl, names);

      if (!node.getSubclasses().isEmpty()) {
        classNameMaps.put(cl.qualifiedName, names);
      }
    }
  }

  private HashMap<String, String> processExternalInterface(StructClass cl) {

    HashMap<String, String> names = new HashMap<String, String>();

    for (String intrName : cl.getInterfaceNames()) {

      HashMap<String, String> mapInt = interfaceNameMaps.get(intrName);
      if (mapInt != null) {
        names.putAll(mapInt);
      }
      else {
        StructClass clintr = context.getClass(intrName);
        if (clintr != null) {
          names.putAll(processExternalInterface(clintr));
        }
      }
    }

    renameClassIdentifiers(cl, names);

    return names;
  }

  private void renameInterfaces() {

    List<ClassWrapperNode> lstInterfaces = getReversePostOrderListIterative(rootInterfaces);

    HashMap<String, HashMap<String, String>> interfaceNameMaps = new HashMap<String, HashMap<String, String>>();

    // rename methods and fields
    for (ClassWrapperNode node : lstInterfaces) {

      StructClass cl = node.getClassStruct();
      HashMap<String, String> names = new HashMap<String, String>();

      // merge informations on super interfaces
      for (String intrName : cl.getInterfaceNames()) {
        HashMap<String, String> mapInt = interfaceNameMaps.get(intrName);
        if (mapInt != null) {
          names.putAll(mapInt);
        }
      }

      renameClassIdentifiers(cl, names);

      interfaceNameMaps.put(cl.qualifiedName, names);
    }

    this.interfaceNameMaps = interfaceNameMaps;
  }

  private void renameAllClasses() {

    // order not important
    List<ClassWrapperNode> lstAllClasses = new ArrayList<ClassWrapperNode>(getReversePostOrderListIterative(rootInterfaces));
    lstAllClasses.addAll(getReversePostOrderListIterative(rootClasses));

    // rename all interfaces and classes
    for (ClassWrapperNode node : lstAllClasses) {
      renameClass(node.getClassStruct());
    }
  }

  private void renameClass(StructClass cl) {

    if (!cl.isOwn()) {
      return;
    }

    String classOldFullName = cl.qualifiedName;

    // TODO: rename packages
    String clsimplename = ConverterHelper.getSimpleClassName(classOldFullName);
    if (helper.toBeRenamed(IIdentifierRenamer.ELEMENT_CLASS, clsimplename, null, null)) {
      String classNewFullName;

      do {
        classNewFullName = ConverterHelper.replaceSimpleClassName(classOldFullName,
                                                                  helper.getNextClassname(classOldFullName, ConverterHelper
                                                                    .getSimpleClassName(classOldFullName)));
      }
      while (context.getClasses().containsKey(classNewFullName));

      interceptor.addName(classOldFullName, classNewFullName);
    }
  }

  private void renameClassIdentifiers(StructClass cl, HashMap<String, String> names) {

    // all classes are already renamed
    String classOldFullName = cl.qualifiedName;
    String classNewFullName = interceptor.getName(classOldFullName);

    if (classNewFullName == null) {
      classNewFullName = classOldFullName;
    }

    // methods
    HashSet<String> setMethodNames = new HashSet<String>();
    for (StructMethod md : cl.getMethods()) {
      setMethodNames.add(md.getName());
    }

    VBStyleCollection<StructMethod, String> methods = cl.getMethods();
    for (int i = 0; i < methods.size(); i++) {

      StructMethod mt = methods.get(i);
      String key = methods.getKey(i);

      boolean isPrivate = mt.hasModifier(CodeConstants.ACC_PRIVATE);

      String name = mt.getName();
      if (!cl.isOwn() || mt.hasModifier(CodeConstants.ACC_NATIVE)) {
        // external and native methods must not be renamed
        if (!isPrivate) {
          names.put(key, name);
        }
      }
      else if (helper.toBeRenamed(IIdentifierRenamer.ELEMENT_METHOD, classOldFullName, name, mt.getDescriptor())) {
        if (isPrivate || !names.containsKey(key)) {
          do {
            name = helper.getNextMethodname(classOldFullName, name, mt.getDescriptor());
          }
          while (setMethodNames.contains(name));

          if (!isPrivate) {
            names.put(key, name);
          }
        }
        else {
          name = names.get(key);
        }

        interceptor.addName(classOldFullName + " " + mt.getName() + " " + mt.getDescriptor(),
                            classNewFullName + " " + name + " " + buildNewDescriptor(false, mt.getDescriptor()));
      }
    }

    // external fields are not being renamed
    if (!cl.isOwn()) {
      return;
    }

    // fields
    // FIXME: should overloaded fields become the same name?
    HashSet<String> setFieldNames = new HashSet<String>();
    for (StructField fd : cl.getFields()) {
      setFieldNames.add(fd.getName());
    }

    for (StructField fd : cl.getFields()) {
      if (helper.toBeRenamed(IIdentifierRenamer.ELEMENT_FIELD, classOldFullName, fd.getName(), fd.getDescriptor())) {
        String newname;

        do {
          newname = helper.getNextFieldname(classOldFullName, fd.getName(), fd.getDescriptor());
        }
        while (setFieldNames.contains(newname));

        interceptor.addName(classOldFullName + " " + fd.getName() + " " + fd.getDescriptor(),
                            classNewFullName + " " + newname + " " + buildNewDescriptor(true, fd.getDescriptor()));
      }
    }
  }

  private String buildNewDescriptor(boolean isField, String descriptor) {

    boolean updated = false;

    if (isField) {
      FieldDescriptor fd = FieldDescriptor.parseDescriptor(descriptor);

      VarType ftype = fd.type;
      if (ftype.type == CodeConstants.TYPE_OBJECT) {
        String newclname = interceptor.getName(ftype.value);
        if (newclname != null) {
          ftype.value = newclname;
          updated = true;
        }
      }

      if (updated) {
        return fd.getDescriptor();
      }
    }
    else {

      MethodDescriptor md = MethodDescriptor.parseDescriptor(descriptor);
      // params
      for (VarType partype : md.params) {
        if (partype.type == CodeConstants.TYPE_OBJECT) {
          String newclname = interceptor.getName(partype.value);
          if (newclname != null) {
            partype.value = newclname;
            updated = true;
          }
        }
      }

      // return value
      if (md.ret.type == CodeConstants.TYPE_OBJECT) {
        String newclname = interceptor.getName(md.ret.value);
        if (newclname != null) {
          md.ret.value = newclname;
          updated = true;
        }
      }

      if (updated) {
        return md.getDescriptor();
      }
    }

    return descriptor;
  }

  private static List<ClassWrapperNode> getReversePostOrderListIterative(List<ClassWrapperNode> roots) {

    List<ClassWrapperNode> res = new ArrayList<ClassWrapperNode>();

    LinkedList<ClassWrapperNode> stackNode = new LinkedList<ClassWrapperNode>();
    LinkedList<Integer> stackIndex = new LinkedList<Integer>();

    HashSet<ClassWrapperNode> setVisited = new HashSet<ClassWrapperNode>();

    for (ClassWrapperNode root : roots) {
      stackNode.add(root);
      stackIndex.add(0);
    }

    while (!stackNode.isEmpty()) {

      ClassWrapperNode node = stackNode.getLast();
      int index = stackIndex.removeLast();

      setVisited.add(node);

      List<ClassWrapperNode> lstSubs = node.getSubclasses();

      for (; index < lstSubs.size(); index++) {
        ClassWrapperNode sub = lstSubs.get(index);
        if (!setVisited.contains(sub)) {
          stackIndex.add(index + 1);

          stackNode.add(sub);
          stackIndex.add(0);

          break;
        }
      }

      if (index == lstSubs.size()) {
        res.add(0, node);

        stackNode.removeLast();
      }
    }

    return res;
  }


  private void buildInheritanceTree() {

    Map<String, ClassWrapperNode> nodes = new HashMap<String, ClassWrapperNode>();
    Map<String, StructClass> classes = context.getClasses();

    List<ClassWrapperNode> rootClasses = new ArrayList<ClassWrapperNode>();
    List<ClassWrapperNode> rootInterfaces = new ArrayList<ClassWrapperNode>();

    for (StructClass cl : classes.values()) {

      if (!cl.isOwn()) {
        continue;
      }

      LinkedList<StructClass> stack = new LinkedList<StructClass>();
      LinkedList<ClassWrapperNode> stackSubnodes = new LinkedList<ClassWrapperNode>();

      stack.add(cl);
      stackSubnodes.add(null);

      while (!stack.isEmpty()) {

        StructClass clstr = stack.removeFirst();
        ClassWrapperNode child = stackSubnodes.removeFirst();

        ClassWrapperNode node = nodes.get(clstr.qualifiedName);
        boolean isNewNode = (node == null);

        if (isNewNode) {
          nodes.put(clstr.qualifiedName, node = new ClassWrapperNode(clstr));
        }

        if (child != null) {
          node.addSubclass(child);
        }

        if (!isNewNode) {
          break;
        }
        else {
          boolean isInterface = clstr.hasModifier(CodeConstants.ACC_INTERFACE);
          boolean found_parent = false;

          if (isInterface) {
            for (String intrName : clstr.getInterfaceNames()) {
              StructClass clparent = classes.get(intrName);
              if (clparent != null) {
                stack.add(clparent);
                stackSubnodes.add(node);
                found_parent = true;
              }
            }
          }
          else {
            if (clstr.superClass != null) { // null iff java/lang/Object
              StructClass clparent = classes.get(clstr.superClass.getString());

              if (clparent != null) {
                stack.add(clparent);
                stackSubnodes.add(node);
                found_parent = true;
              }
            }
          }

          if (!found_parent) { // no super class or interface
            (isInterface ? rootInterfaces : rootClasses).add(node);
          }
        }
      }
    }

    this.rootClasses = rootClasses;
    this.rootInterfaces = rootInterfaces;
  }
}
