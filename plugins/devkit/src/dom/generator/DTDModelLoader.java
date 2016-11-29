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

import com.wutka.dtd.*;
import org.apache.xerces.xni.parser.XMLEntityResolver;

import java.io.File;
import java.util.*;

/**
 * @author Gregory.Shrago
 * @author Konstantin Bulenkov
 */
public class DTDModelLoader implements ModelLoader {
  private ModelDesc model;

  public void loadModel(ModelDesc model, Collection<File> schemas, XMLEntityResolver resolver) throws Exception {
    this.model = model;
    for (File dtdFile : schemas) {
      String fileName = dtdFile.getPath();
      if (dtdFile.isDirectory() || !fileName.endsWith(".dtd") || fileName.endsWith("datatypes.dtd")) {
        Util.log("skipping " + fileName);
        continue;
      }
      Util.log("loading " + fileName+"..");
      String ns = fileName.substring(fileName.lastIndexOf(File.separatorChar) + 1);
      //loadDTDByXerces(ns, dtdFile, resolver);

      loadDTDByWutka(ns, dtdFile);

    }
  }

  private NamespaceDesc ensureNamespaceExists(String ns) {
    if (!model.nsdMap.containsKey(ns)) {
      Util.log("Adding default ns desc for: " + ns);
      NamespaceDesc nsd = new NamespaceDesc(ns, model.nsdMap.get(""));
      model.nsdMap.put(ns, nsd);
    }
    return model.nsdMap.get(ns);
  }


  private void loadDTDByWutka(String ns, File dtdFile) throws Exception {
    DTDParser parser = new DTDParser(dtdFile, false);
    // Parse the DTD and ask the parser to guess the root element
    DTD dtd = parser.parse(true);
    checkDTDRootElement(dtd);
    processDTD(ns, dtd, model.jtMap, model.nsdMap);
  }


  private void processDTD(String namespace, DTD dtd, Map<String, TypeDesc> jtMap, Map<String, NamespaceDesc> nsdMap) {
    final NamespaceDesc nsd = ensureNamespaceExists(namespace);
    if (nsd.skip) return;
    final ArrayList<String> resultQNames = new ArrayList<>();
    final DTDElement[] elements = new DTDElement[dtd.elements.size()];
    int ptr = 1;

    final HashSet<DTDElement> visitedElements = new HashSet<>();
    elements[0] = dtd.rootElement;

    while (--ptr > -1) {
      final DTDElement el = elements[ptr];
      visitedElements.add(el);
      final String typeName = model.toJavaTypeName(el.name, namespace);
      final String typeQName = model.toJavaQualifiedTypeName(namespace, typeName, false);
      if (resultQNames.contains(typeQName)) {
        continue;
      } else {
        resultQNames.add(typeQName);
      }
      final TypeDesc td = new TypeDesc(el.name, namespace, typeName, TypeDesc.TypeEnum.CLASS);
      boolean duplicates = false;
      if ((el.content instanceof DTDAny) || (el.content instanceof DTDMixed)) {
        FieldDesc fd = new FieldDesc(FieldDesc.SIMPLE, "value", "String", null, "null", false);
        fd.realIndex = td.fdMap.size();
        td.fdMap.put(fd.name, fd);
      }
      for (Object o : el.attributes.keySet()) {
        String attrName = (String) o;
        DTDAttribute attr = (DTDAttribute) el.attributes.get(attrName);
        if (attr.decl == DTDDecl.FIXED || "ID".equals(attr.type)) {
          continue;
        }
        boolean required = attr.decl == DTDDecl.REQUIRED;
        FieldDesc fd1 = new FieldDesc(FieldDesc.ATTR, Util.toJavaFieldName(attrName), "String", null, "\"\"", required);
        fd1.tagName = attrName;
        fd1.documentation = "Attribute " + attrName + "";
        fd1.realIndex = td.fdMap.size();
        duplicates = Util.addToNameMap(td.fdMap, fd1, false) || duplicates;
      }
      final ArrayList<List<DTDItem>> choiceList = new ArrayList<>();
      final LinkedList<Entry> plist = new LinkedList<>();
      if (el.content instanceof DTDContainer) {
      //if ((el.content instanceof DTDChoice) || (el.content instanceof DTDSequence)) {
        plist.add(new Entry(el.content, false, true));
      }
      while (!plist.isEmpty()) {
        final Entry pentry = plist.removeFirst();

        final DTDItem p = pentry.p;

        if (p instanceof DTDName) {
          final DTDName n = (DTDName) p;
          final DTDElement nel = (DTDElement) dtd.elements.get(n.value);
          final String pName = n.value;
          final FieldDesc fd1 = new FieldDesc(FieldDesc.STR, Util.toJavaFieldName(pName), pName, null, "null", pentry.required && (n.cardinal == DTDCardinal.ONEMANY || n.cardinal == DTDCardinal.NONE));
          fd1.tagName = pName;
          if (nel != null) {
            fd1.documentation = parseDTDItemDocumentation(dtd, nel, "Type " + nel.name + " documentation");
          }
          if (nel == null) {
            fd1.type = model.toJavaTypeName(fd1.tagName, namespace);
          } else if (nel.content instanceof DTDEmpty || nel.content instanceof DTDAny) {
            boolean hasAttrFields = false;
            boolean hasTextContents = nel.content instanceof DTDAny;
            for (Object o : nel.attributes.values()) {
              DTDAttribute attr = (DTDAttribute) o;
              if (attr.decl != DTDDecl.FIXED && !"ID".equals(attr.type)) {
                hasAttrFields = true;
                break;
              }
            }
            if (hasAttrFields || hasTextContents) {
              fd1.clType = FieldDesc.OBJ;
              fd1.type = model.toJavaTypeName(fd1.tagName, namespace);
              fd1.contentQualifiedName = model.toJavaQualifiedTypeName(namespace, fd1.name, false);
              fd1.def = "null";
              // next type
              if (!visitedElements.contains(nel)) {
                elements[ptr++] = nel;
              }
            } else {
              fd1.clType = FieldDesc.BOOL;
              fd1.type = "boolean";
              fd1.def = "false";
            }
          } else if (nel.content instanceof DTDContainer) {
            boolean hasAttrFields = false;
            boolean hasTextField = false;
            if ((nel.content instanceof DTDMixed) && ((DTDMixed) nel.content).getItemsVec().size() == 1) {
              hasTextField = true;
              for (Object o : nel.attributes.values()) {
                final DTDAttribute attr = (DTDAttribute) o;
                if (attr.decl != DTDDecl.FIXED && !"ID".equals(attr.type)) {
                  hasAttrFields = true;
                  break;
                }
              }
            }
            if (hasTextField && !hasAttrFields) {
              fd1.clType = FieldDesc.STR;
              fd1.type = "String";
              fd1.def = "null";
            } else {
              fd1.clType = FieldDesc.OBJ;
              fd1.type = model.toJavaTypeName(fd1.tagName, namespace);
              fd1.contentQualifiedName = model.toJavaQualifiedTypeName(namespace, fd1.tagName, false);
              // next type
              if (!visitedElements.contains(nel)) {
                elements[ptr++] = nel;
              }
            }
          } else {
            fd1.type = "ERROR:Name";
          }
          if ((pentry.many || n.cardinal.type >= 2) && fd1.clType != FieldDesc.BOOL) {
            fd1.elementType = fd1.type;
            fd1.elementName = fd1.name;
            fd1.type = "List<" + fd1.elementType + ">";
            fd1.name = Util.pluralize(fd1.name);
            fd1.def = "new ArrayList(0)";
            fd1.clType = -fd1.clType;
            fd1.comment = "array of " + fd1.elementType;
          }
          fd1.realIndex = td.fdMap.size();
          duplicates = Util.addToNameMap(td.fdMap, fd1, false) || duplicates;
        } else if (p instanceof DTDContainer) {
          final DTDContainer cont = (DTDContainer) p;
          final boolean isChoice = cont instanceof DTDChoice;
          // 0 - NONE, 1 - OPT, 2 - ZEROMANY, 3 - ONEMANY
          final boolean required = !isChoice && pentry.required && p.cardinal != DTDCardinal.ZEROMANY && p.cardinal != DTDCardinal.OPTIONAL;
          final boolean many = p.cardinal == DTDCardinal.ONEMANY || p.cardinal == DTDCardinal.ZEROMANY;
          List<DTDItem> l = cont.getItemsVec();
          if (!many && isChoice) {
            choiceList.add(l);
          }
          for (DTDItem aL : l) {
            plist.add(new Entry(aL, many, required));
          }
        } else {
          Util.logerr("unknown item " + p);
        }
      }
      td.duplicates = duplicates;
      td.documentation = parseDTDItemDocumentation(dtd, el, "Type " + el.name + " documentation");
      jtMap.put(model.toJavaQualifiedTypeName(namespace, td.name, false), td);
      int i = 0;
      for (FieldDesc fd : td.fdMap.values()) {
        fd.idx = i++;
      }
      for (List<DTDItem> l : choiceList) {
        ArrayList<DTDItem> clist = new ArrayList<>();
        LinkedList<DTDItem> elist = new LinkedList<>();
        for (i = 0; i < l.size(); i++) {
          elist.add(l.get(i));
        }
        while (!elist.isEmpty()) {
          DTDItem p = elist.removeFirst();
          if (p instanceof DTDContainer) {
            List<DTDItem> l2 = ((DTDContainer) p).getItemsVec();
            for (DTDItem aL2 : l2) {
              elist.addFirst(aL2);
            }
          } else if (p instanceof DTDName) {
            clist.add(p);
          }
        }
        boolean choiceOpt = true;
        FieldDesc[] choice = new FieldDesc[clist.size()];
        for (i = 0; i < choice.length; i++) {
          DTDName p = (DTDName) clist.get(i);
          String s = Util.toJavaFieldName(p.value);
          FieldDesc fd = td.fdMap.get(s);
          if (fd == null) {
            fd = td.fdMap.get(Util.pluralize(s));
            if (fd == null) {
              Util.logerr("uknown choice element: " + s);
              continue;
            }
          }
          choice[i] = fd;
          choice[i].choice = choice;
          if (fd.required) choiceOpt = false;
        }
        for (i = 0; i < choice.length; i++) {
          choice[i].choiceOpt = choiceOpt;
        }
      }
    }
    List<DTDEntity> entList = dtd.getItemsByType(DTDEntity.class);
    for (DTDEntity entity : entList) {
      String value = entity.value;
      if (!value.startsWith("(") || !value.endsWith(")")) {
        continue;
      }
      String typeName = model.toJavaTypeName(entity.name, namespace);
      TypeDesc td = new TypeDesc(entity.name, namespace, typeName, TypeDesc.TypeEnum.ENUM);
      StringTokenizer st = new StringTokenizer(value, "(|)");
      while (st.hasMoreTokens()) {
        final String s = st.nextToken();
        td.fdMap.put(s, new FieldDesc(Util.computeEnumConstantName(s, td.name), s));
      }
      td.documentation = parseDTDItemDocumentation(dtd, entity, "Type " + entity.name + " documentation");
      jtMap.put(model.toJavaQualifiedTypeName(namespace, td.name, true), td);
    }
  }

  private static String parseDTDItemDocumentation(DTD dtd, DTDOutput obj, String title) {
    int elementIndex = dtd.items.indexOf(obj);
    if (elementIndex < 1) return null;
    Object prev = dtd.items.get(elementIndex - 1);
    if (!(prev instanceof DTDComment)) return null;
    DTDComment comment = (DTDComment) prev;
    return title + "\n" + "<pre>\n" + comment.getText() + "\n</pre>";
  }

  static class Entry {
    boolean required;
    boolean many;
    DTDItem p;

    Entry(DTDItem p, boolean many, boolean required) {
      this.required = required;
      this.many = many;
      this.p = p;
    }

    Entry parent;
    DTDItem it;
    Vector choice;
    int num;

    Entry(Entry parent, DTDItem it, Vector choice, int num) {
      this.parent = parent;
      this.it = it;
      this.choice = choice;
      this.num = num;
    }
  }


  private static void checkDTDRootElement(DTD dtd) throws Exception {
    if (dtd.rootElement == null) {
      StringBuffer sb = new StringBuffer("Empty root: possible elements: ");
      HashMap map = new HashMap(dtd.elements);
      for (Object o : dtd.elements.values()) {
        DTDElement el = (DTDElement) o;
        if (el.content instanceof DTDContainer) {
          for (Object obj : ((DTDContainer) el.content).getItemsVec()) {
            if (obj instanceof DTDName) {
              map.remove(((DTDName) obj).value);
            }
          }
        }
      }
      if (dtd.rootElement != null) return;
      sb.append(map.size()).append(map.keySet());
      throw new Exception(sb.toString());
    }
  }

}
