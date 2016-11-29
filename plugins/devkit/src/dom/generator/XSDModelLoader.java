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

import org.apache.xerces.xs.*;
import org.apache.xerces.impl.xs.XSComplexTypeDecl;
import org.apache.xerces.impl.xs.XSParticleDecl;
import org.apache.xerces.impl.xs.XSAttributeGroupDecl;
import org.apache.xerces.impl.xs.XSElementDecl;
import org.apache.xerces.impl.xs.util.XSObjectListImpl;
import org.apache.xerces.impl.dv.XSSimpleType;
import org.apache.xerces.impl.dv.xs.XSSimpleTypeDecl;
import org.apache.xerces.xni.parser.XMLEntityResolver;
import org.w3c.dom.bootstrap.DOMImplementationRegistry;
import org.w3c.dom.DOMConfiguration;
import org.w3c.dom.DOMErrorHandler;
import org.w3c.dom.DOMError;
import org.w3c.dom.TypeInfo;

import javax.xml.namespace.QName;
import java.io.File;
import java.util.*;

/**
 * @author Gregory.Shrago
 * @author Konstantin Bulenkov
 */
public class XSDModelLoader implements ModelLoader {
  private static final boolean TEXT_ELEMENTS_ARE_COMPLEX = false;


  private ModelDesc model;

  public void loadModel(ModelDesc model, Collection<File> files, XMLEntityResolver resolver) throws Exception {
    this.model = model;
    processSchemas(files, resolver);
  }

  public static boolean checkComplexType(XSTypeDefinition td) {
    if (td.getTypeCategory() != XSTypeDefinition.COMPLEX_TYPE) return false;
    XSComplexTypeDefinition ctd = (XSComplexTypeDefinition) td;
    if (ctd.getContentType() == XSComplexTypeDefinition.CONTENTTYPE_ELEMENT) {
      return true;
    }
    if ((td instanceof XSComplexTypeDecl) && ((XSComplexTypeDecl) td).getAbstract()) return true;
    if (TEXT_ELEMENTS_ARE_COMPLEX) return true;
    if (ctd.getAttributeUses() != null) {
      for (int i = 0; i < ctd.getAttributeUses().getLength(); i++) {
        XSSimpleTypeDefinition xsstd = ((XSAttributeUse) ctd.getAttributeUses().item(i)).getAttrDeclaration().getTypeDefinition();
        if ("ID".equals(xsstd.getName())) continue;
        return true;
      }
    }
    return false;
  }

  public static boolean checkEnumType(XSTypeDefinition td) {
    final XSSimpleTypeDefinition st;
    if (td.getTypeCategory() == XSTypeDefinition.COMPLEX_TYPE) {
      XSComplexTypeDefinition ctd = (XSComplexTypeDefinition) td;
      if (ctd.getContentType() != XSComplexTypeDefinition.CONTENTTYPE_SIMPLE) return false;
      if (ctd.getAttributeUses() != null) {
        for (int i = 0; i < ctd.getAttributeUses().getLength(); i++) {
          XSSimpleTypeDefinition xsstd = ((XSAttributeUse) ctd.getAttributeUses().item(i)).getAttrDeclaration().getTypeDefinition();
          if ("ID".equals(xsstd.getName())) continue;
          return false;
        }
      }
      st = ctd.getSimpleType();
    } else if (td.getTypeCategory() == XSTypeDefinition.SIMPLE_TYPE) {
      st = (XSSimpleTypeDefinition) td;
    } else {
      return false;
    }
    return st.getLexicalEnumeration() != null && st.getLexicalEnumeration().getLength() != 0;
  }

  private static boolean checkBooleanType(XSTypeDefinition td) {
    if (td.getTypeCategory() != XSTypeDefinition.SIMPLE_TYPE) return false;
    final XSSimpleTypeDefinition st = ((XSSimpleTypeDefinition) td);
    final XSObjectList facets = st.getFacets();
    for (int i = 0; i < facets.getLength(); i++) {
      final XSFacet facet = (XSFacet) facets.item(i);
      if (facet.getFacetKind() == XSSimpleTypeDefinition.FACET_LENGTH) {
        if ("0".equals(facet.getLexicalFacetValue())) {
          return true;
        }
      }
    }
    return false;
  }


  private XSModel loadSchema(File schemaFile, XMLEntityResolver resolver) throws Exception {
    // get DOM Implementation using DOM Registry
    System.setProperty(
            DOMImplementationRegistry.PROPERTY,
            "org.apache.xerces.dom.DOMXSImplementationSourceImpl");
    DOMImplementationRegistry registry = DOMImplementationRegistry.newInstance();
    XSImplementation impl = (XSImplementation) registry.getDOMImplementation("XS-Loader");
    XSLoader schemaLoader = impl.createXSLoader(null);
    DOMConfiguration config = schemaLoader.getConfig();

    // create Error Handler
    DOMErrorHandler errorHandler = new DOMErrorHandler() {
      public boolean handleError(DOMError domError) {
        Util.log("DOMError: " + domError.getMessage());
        Object relatedException = domError.getRelatedException();
        if (relatedException != null) {
          Util.log("DOMError: " + relatedException);
          if (relatedException instanceof Throwable) {
            ((Throwable) relatedException).printStackTrace(System.out);
          }
        }
        return false;
      }
    };
    // set error handler
    config.setParameter("error-handler", errorHandler);
    // set validation feature
    config.setParameter("validate", Boolean.TRUE);
    // parse document
    config.setParameter("error-handler", errorHandler);
    config.setParameter("http://apache.org/xml/properties/internal/entity-resolver", resolver);

    XSModel model = schemaLoader.loadURI(schemaFile.getAbsolutePath());
    if (model == null) return null;
    XSNamedMap components = model.getComponents(XSConstants.ELEMENT_DECLARATION);
    for (int i = 0; i < components.getLength(); i++) {
      XSObject obj = components.item(i);
      QName qname = new QName(obj.getNamespace(), obj.getName());
      String file = this.model.qname2FileMap.get(qname);
      this.model.qname2FileMap.put(qname, (file == null ? "" : file + ";") + schemaFile.getName());
    }
    components = model.getComponents(XSConstants.TYPE_DEFINITION);
    for (int i = 0; i < components.getLength(); i++) {
      XSObject obj = components.item(i);
      QName qname = new QName(obj.getNamespace(), obj.getName());
      String file = this.model.qname2FileMap.get(qname);
      this.model.qname2FileMap.put(qname, (file == null ? "" : file) + ":" + schemaFile.getName() + ":");
    }
    return model;
  }

  public void processSchemas(Collection<File> schemas, XMLEntityResolver resolver) throws Exception {
    Map<String, NamespaceDesc> nsdMap = model.nsdMap;
    Map<String, TypeDesc> jtMap = model.jtMap;
    final NamespaceDesc nsdDef = nsdMap.get("");
    final ArrayList<XSModel> models = new ArrayList<>();
    final HashMap<String, XSTypeDefinition> types = new HashMap<>();
    for (File schemaFile : schemas) {
      String fileName = schemaFile.getPath();
      if (schemaFile.isDirectory() || !fileName.endsWith(".xsd")) {
        Util.log("skipping " + fileName);
        continue;
      }
      Util.log("loading " + fileName + "..");

      final XSModel model = loadSchema(schemaFile, resolver);
      if (model == null) continue;

      final StringList namespaceList = model.getNamespaces();
      for (int i = 0; i < namespaceList.getLength(); i++) {
        String ns = namespaceList.item(i);
        if (!nsdMap.containsKey(ns)) {
          Util.log("Adding default namespace desc for: " + ns);
          NamespaceDesc nsd = new NamespaceDesc(ns, nsdDef);
          nsdMap.put(ns, nsd);
        }
      }
      models.add(model);
      final XSNamedMap typeDefMap = model.getComponents(XSConstants.TYPE_DEFINITION);
      for (int i = 0; i < typeDefMap.getLength(); i++) {
        XSTypeDefinition o = (XSTypeDefinition) typeDefMap.item(i);
        NamespaceDesc nsd = nsdMap.get(o.getNamespace());
        if (nsd != null && nsd.skip) continue;
        final String key = o.getName() + "," + o.getNamespace();
        types.put(key, o);
      }
      final XSNamedMap elementDeclMap = model.getComponents(XSConstants.ELEMENT_DECLARATION);
      for (int i = 0; i < elementDeclMap.getLength(); i++) {
        XSElementDeclaration o = (XSElementDeclaration) elementDeclMap.item(i);
        if (o.getTypeDefinition().getAnonymous() && (o.getTypeDefinition() instanceof XSComplexTypeDefinition)) {
          //types.put(o.getName() + "," + o.getNamespace(), o);
          XSComplexTypeDefinition ctd = makeTypeFromAnonymous(o);
          NamespaceDesc nsd = nsdMap.get(o.getNamespace());
          if (nsd != null && nsd.skip) continue;
          final String key = ctd.getName() + "," + ctd.getNamespace();
          types.put(key, ctd);
        }
      }
    }
    Util.log(types.size() + " elements loaded, processing..");
    ArrayList<XSTypeDefinition> toProcess = new ArrayList<>(types.values());
    ArrayList<XSComplexTypeDefinition> toAdd = new ArrayList<>();
    for (ListIterator<XSTypeDefinition> it = toProcess.listIterator(); it.hasNext();) {
      XSTypeDefinition td = it.next();
      Util.log("processing " + td.getName() + "," + td.getNamespace() + "..");
      if (checkComplexType(td)) {
        processType((XSComplexTypeDefinition) td, models, jtMap, nsdMap, toAdd);
      } else if (checkEnumType(td)) {
        processEnumType(td, jtMap, nsdMap);
      }
      if (toAdd.size() != 0) {
        for (XSComplexTypeDefinition o : toAdd) {
          final String key = o.getName() + "," + o.getNamespace();
          if (!types.containsKey(key)) {
            Util.log("  adding " + o.getName() + "," + o.getNamespace());
            types.put(key, o);
            it.add(o);
            it.previous();
          } else {
            Util.logwarn(key + " already exists");
          }
        }
        toAdd.clear();
      }
    }
  }

  private XSComplexTypeDefinition makeTypeFromAnonymous(XSObject o) {
    final XSComplexTypeDecl ctd = new XSComplexTypeDecl();
    if (o instanceof XSElementDeclaration && ((XSElementDeclaration) o).getTypeDefinition() instanceof XSComplexTypeDecl) {
      final XSComplexTypeDecl ctd1 = (XSComplexTypeDecl) ((XSElementDeclaration) o).getTypeDefinition();
      final XSObjectListImpl annotations = ctd1.getAnnotations() instanceof XSObjectListImpl ? (XSObjectListImpl) ctd1.getAnnotations() : new XSObjectListImpl();
      ctd.setValues(o.getName(), ctd1.getNamespace(), ctd1.getBaseType(), ctd1.getDerivationMethod(),
              ctd1.getFinal(), ctd1.getProhibitedSubstitutions(), ctd1.getContentType(),
              ctd1.getAbstract(), ctd1.getAttrGrp(), (XSSimpleType) ctd1.getSimpleType(),
              (XSParticleDecl) ctd1.getParticle(), annotations);
      ctd.setName(o.getName() + Util.ANONYMOUS_ELEM_TYPE_SUFFIX);
    } else if (o instanceof XSAttributeDeclaration) {
      final XSSimpleTypeDecl ctd1 = (XSSimpleTypeDecl) ((XSAttributeDeclaration) o).getTypeDefinition();
      final XSObjectListImpl annotations = ctd1.getAnnotations() instanceof XSObjectListImpl ? (XSObjectListImpl) ctd1.getAnnotations() : new XSObjectListImpl();
      ctd.setValues(o.getName(), ctd1.getNamespace(), ctd1.getBaseType(), XSConstants.DERIVATION_RESTRICTION,
              ctd1.getFinal(), (short) 0, XSComplexTypeDefinition.CONTENTTYPE_SIMPLE,
              false, new XSAttributeGroupDecl(), ctd1,
              null, annotations);
      ctd.setName(o.getName() + Util.ANONYMOUS_ATTR_TYPE_SUFFIX);
    }

    model.qname2FileMap.put(new QName(ctd.getNamespace(), ctd.getName()), model.qname2FileMap.get(new QName(o.getNamespace(), o.getName())));
    return ctd;
  }

  public void processEnumType(XSTypeDefinition def, Map<String, TypeDesc> jtMap, Map<String, NamespaceDesc> nsdMap) throws Exception {
    boolean complexType = def instanceof XSComplexTypeDefinition;
    if (!nsdMap.containsKey(def.getNamespace())) {
      Util.log("Namespace desc not found for: " + def);
    }
    final String typeName = toJavaTypeName(def, nsdMap);
    final TypeDesc td = new TypeDesc(def.getName(), def.getNamespace(), typeName, TypeDesc.TypeEnum.ENUM);
    final XSComplexTypeDefinition ct = complexType ? (XSComplexTypeDefinition) def : null;
    final XSSimpleTypeDefinition st = (XSSimpleTypeDefinition) (complexType ? ((XSComplexTypeDefinition) def).getSimpleType() : def);
    for (int i = 0; i < st.getLexicalEnumeration().getLength(); i++) {
      final String s = st.getLexicalEnumeration().item(i);
      td.fdMap.put(s, new FieldDesc(Util.computeEnumConstantName(s, td.name), s));
    }

    final XSObjectList anns = complexType ? ct.getAnnotations() : st.getAnnotations();

    td.documentation = parseAnnotationString("Enumeration " + def.getNamespace() + ":" + def.getName() + " documentation", anns != null && anns.getLength() > 0 ? ((XSAnnotation) anns.item(0)).getAnnotationString() : null);
    jtMap.put(model.toJavaQualifiedTypeName(def, nsdMap, true), td);
  }

  public void processType(XSComplexTypeDefinition def, List<XSModel> models, Map<String, TypeDesc> jtMap, Map<String, NamespaceDesc> nsdMap, ArrayList<XSComplexTypeDefinition> toAdd) throws Exception {
    if (!nsdMap.containsKey(def.getNamespace())) {
      Util.log("Namespace desc not found for: " + def);
    }
    String typeName = toJavaTypeName(def, nsdMap);
    TypeDesc td = jtMap.get(model.toJavaQualifiedTypeName(def, nsdMap, false));
    if (td != null) {
      if (td.fdMap.size() == 0) {
        // Util.log("Reusing forward decl: "+typeName);
      } else {
        Util.logerr("merging: type names collision: " + typeName);
      }
    } else {
      td = new TypeDesc(def.getName(), def.getNamespace(), typeName, TypeDesc.TypeEnum.CLASS);
    }
    XSObjectList anns = def.getAnnotations();
    td.documentation = parseAnnotationString("Type " + def.getNamespace() + ":" + def.getName() + " documentation",
            anns != null && anns.getLength() > 0 ? ((XSAnnotation) anns.item(0)).getAnnotationString() : null);
    TypeDesc tdBase = null;
    if (checkComplexType(def.getBaseType())) {
      XSComplexTypeDefinition base = (XSComplexTypeDefinition) def.getBaseType();
      String typeNameBase = toJavaTypeName(base, nsdMap);
      if ((tdBase = jtMap.get(model.toJavaQualifiedTypeName(base, nsdMap, false))) == null) {
        // logwarn("forward decl: "+et);
        tdBase = new TypeDesc(base.getName(), base.getNamespace(), typeNameBase, TypeDesc.TypeEnum.CLASS);
        jtMap.put(model.toJavaQualifiedTypeName(base, nsdMap, false), tdBase);
      }
    }
    if (def.getSimpleType() != null || def.getContentType() == XSComplexTypeDefinition.CONTENTTYPE_MIXED) {
      FieldDesc fd = new FieldDesc(FieldDesc.SIMPLE, "value", "String", null, "null", true);
      fd.realIndex = td.fdMap.size();
      td.fdMap.put(fd.name, fd);
    }
    XSObjectList attrs = def.getAttributeUses();
    for (int i = 0; i < attrs.getLength(); i++) {
      XSAttributeUse au = (XSAttributeUse) attrs.item(i);
      XSAttributeDeclaration ad = au.getAttrDeclaration();
      XSSimpleTypeDefinition atd = ad.getTypeDefinition();
      XSAnnotation ann = ad.getAnnotation();
      String documentation = parseAnnotationString("Attribute " + ad.getNamespace() + ":" + ad.getName() + " documentation", ann != null ? ann.getAnnotationString() : null);
      // skip "ID" and "FIXED"
      if ("ID".equals(atd.getName())) continue;
      // "language", "dewey-versionType", "boolean"
      if (ad.getConstraintType() == XSConstants.VC_FIXED) continue;
      FieldDesc fd1 = new FieldDesc(FieldDesc.ATTR, Util.toJavaFieldName(ad.getName()), "String", null, "null", au.getRequired());
      fd1.tagName = ad.getName();
      fd1.documentation = documentation;
      fd1.realIndex = td.fdMap.size();
      td.duplicates = Util.addToNameMap(td.fdMap, fd1, false) || td.duplicates;
      if (checkEnumType(ad.getTypeDefinition())) {
        XSTypeDefinition etRoot = ad.getTypeDefinition();
        if (etRoot.getAnonymous()) {
          etRoot = makeTypeFromAnonymous(ad);
          if (toAdd != null) toAdd.add((XSComplexTypeDefinition) etRoot);
        }
        fd1.type = toJavaTypeName(etRoot, nsdMap);
        fd1.contentQualifiedName = model.toJavaQualifiedTypeName(etRoot, nsdMap, true);
        // forward decl
        if (jtMap.get(fd1.contentQualifiedName) == null) {
          // logwarn("forward decl: "+et);
          TypeDesc ftd = new TypeDesc(etRoot.getName(), etRoot.getNamespace(), fd1.type, TypeDesc.TypeEnum.ENUM);
          jtMap.put(fd1.contentQualifiedName, ftd);
//          // anonymous (simple type) enum
//          if (ad.getTypeDefinition().getAnonymous()) {
//            processEnumType(ad.getTypeDefinition(), jtMap, nsdMap);
//          }
        }
      } else {
        fd1.simpleTypesString = getSimpleTypesString(ad.getTypeDefinition());
      }
    }
    LinkedList<PEntry> plist = new LinkedList<>();
    if (def.getParticle() != null) {
      plist.add(new PEntry(def.getParticle(), false));
    }
    processParticles(def, plist, nsdMap, jtMap, td, models, toAdd, tdBase);
    jtMap.put(model.toJavaQualifiedTypeName(def, nsdMap, false), td);
    if (td.fdMap.size() == 1 && def.getSimpleType() != null) {
      // calc type hierarchy for simple content
      FieldDesc fd = td.fdMap.get("value");
      fd.simpleTypesString = getSimpleTypesString(def);
    }
  }

  private static String getSimpleTypesString(XSTypeDefinition et) {
    StringBuffer typesHierarchy = new StringBuffer();
    while (et != null && !"anySimpleType".equals(et.getName()) && !"anyType".equals(et.getName()) && et.getNamespace() != null) {
      typesHierarchy.append(et.getNamespace().substring(et.getNamespace().lastIndexOf("/") + 1)).append(":").append(et.getName()).append(";");
      if (et instanceof XSSimpleType) {
        XSSimpleType simpleType = (XSSimpleType) et;
        if (simpleType.getVariety() == XSSimpleTypeDefinition.VARIETY_LIST
                || simpleType.getVariety() == XSSimpleTypeDefinition.VARIETY_UNION) {
          XSObjectList list = simpleType.getMemberTypes();
          if (list.getLength() > 0) {
            typesHierarchy.append("{");
            for (int i = 0; i < list.getLength(); i++) {
              typesHierarchy.append(getSimpleTypesString((XSTypeDefinition) list.item(i)));
            }
            typesHierarchy.append("}");
          }
        }
      }
      et = et.getBaseType();
    }
    return typesHierarchy.toString();
  }

  private TypeDesc processGroup(XSModelGroup modelGroup, List<XSModel> models, Map<String, TypeDesc> jtMap, Map<String, NamespaceDesc> nsdMap) {
    XSModelGroupDefinition def = null;
    for (XSModel xsModel : models) {
      XSNamedMap map = xsModel.getComponents(XSConstants.MODEL_GROUP_DEFINITION);
      for (int i = 0; i < map.getLength(); i++) {
        XSModelGroupDefinition mg = (XSModelGroupDefinition) map.item(i);
        final XSModelGroup xsModelGroup = mg.getModelGroup();
        if (xsModelGroup == modelGroup || xsModelGroup.toString().equals(modelGroup.toString())) {
          def = mg;
          break;
        }
      }
    }
    if (def == null) return null;
    if (!nsdMap.containsKey(def.getNamespace())) {
      Util.log("Namespace desc not found for: " + def);
    }
    String typeName = toJavaTypeName(def, nsdMap);
    final String typeQName = model.toJavaQualifiedTypeName(def, nsdMap, false);
    TypeDesc td = jtMap.get(typeQName);
    if (td != null) {
      if (td.type == TypeDesc.TypeEnum.GROUP_INTERFACE) {
        return td;
      } else {
        Util.logerr("type-group conflict: " + typeName);
        return null;
      }
    } else {
      td = new TypeDesc(def.getName(), def.getNamespace(), typeName, TypeDesc.TypeEnum.GROUP_INTERFACE);
    }

    XSAnnotation ann = def.getAnnotation();
    td.documentation = parseAnnotationString("Type " + def.getNamespace() + ":" + def.getName() + " documentation",
            ann == null ? null : ann.getAnnotationString());
    td.type = TypeDesc.TypeEnum.GROUP_INTERFACE;
    LinkedList<PEntry> plist = new LinkedList<>();
    for (int i = 0; i < def.getModelGroup().getParticles().getLength(); i++) {
      XSParticle p = (XSParticle) def.getModelGroup().getParticles().item(i);
      plist.add(new PEntry(p, false));
    }
    processParticles(def, plist, nsdMap, jtMap, td, models, null, null);
    jtMap.put(typeQName, td);
    return td;
  }

  private void processParticles(XSObject def, LinkedList<PEntry> plist, Map<String, NamespaceDesc> nsdMap, Map<String, TypeDesc> jtMap, TypeDesc td, List<XSModel> models, ArrayList<XSComplexTypeDefinition> toAdd, TypeDesc baseClass) {
    final boolean globalMerge = jtMap.containsKey(model.toJavaQualifiedTypeName(def, nsdMap, td.type == TypeDesc.TypeEnum.ENUM));
    final HashMap<XSParticle, String> globalChoice = new HashMap<>();
    final ArrayList<XSObjectList> choiceList = new ArrayList<>();
    final ArrayList<TypeDesc> supers = new ArrayList<>();
    if (baseClass != null) {
      supers.add(baseClass);
    }
    while (!plist.isEmpty()) {
      final PEntry pentry = plist.removeFirst();
      final XSParticle p = pentry.p;
      if (p.getTerm() instanceof XSElementDecl) {
        final XSElementDecl el = (XSElementDecl) p.getTerm();
        if (el.getConstraintType() == XSConstants.VC_FIXED) continue;
        XSTypeDefinition etRoot = el.getTypeDefinition();
        XSTypeDefinition et = etRoot;
        XSAnnotation ann = el.getAnnotation();
        String documentation = parseAnnotationString("Element " + el.getNamespace() + ":" + el.getName() + " documentation", ann != null ? ann.getAnnotationString() : null);
        final FieldDesc fd1 = new FieldDesc(FieldDesc.STR, Util.toJavaFieldName(el.getName()), et.getName(), null, "null", !pentry.many && p.getMinOccurs() > 0);
        fd1.documentation = documentation;
        fd1.tagName = el.getName();
        while (et.getBaseType() != null && !"anySimpleType".equals(et.getBaseType().getName()) && !"anyType".equals(et.getBaseType().getName())) {
          et = et.getBaseType();
        }
        if (checkEnumType(etRoot)) {
          if (etRoot.getAnonymous()) {
            etRoot = makeTypeFromAnonymous(el);
            if (toAdd != null) toAdd.add((XSComplexTypeDefinition) etRoot);
          }
          fd1.type = toJavaTypeName(etRoot, nsdMap);
          fd1.clType = FieldDesc.OBJ;
          fd1.contentQualifiedName = model.toJavaQualifiedTypeName(etRoot, nsdMap, true);
          // forward decl
          if (!jtMap.containsKey(fd1.contentQualifiedName)) {
            // logwarn("forward decl: "+et);
            TypeDesc ftd = new TypeDesc(etRoot.getName(), etRoot.getNamespace(), fd1.type, TypeDesc.TypeEnum.ENUM);
            jtMap.put(fd1.contentQualifiedName, ftd);
          }
        } else if (checkComplexType(etRoot)) {
          if (etRoot.getAnonymous()) {
            etRoot = makeTypeFromAnonymous(el);
            if (toAdd != null) toAdd.add((XSComplexTypeDefinition) etRoot);
          }
          fd1.type = toJavaTypeName(etRoot, nsdMap);
          fd1.clType = FieldDesc.OBJ;
          fd1.contentQualifiedName = model.toJavaQualifiedTypeName(etRoot, nsdMap, false);
          // forward decl
          if (jtMap.get(fd1.contentQualifiedName) == null) {
            //logwarn("forward decl: "+etRoot);
            jtMap.put(fd1.contentQualifiedName, new TypeDesc(etRoot.getName(), etRoot.getNamespace(), fd1.type, TypeDesc.TypeEnum.CLASS));
          }
        } else if (checkBooleanType(etRoot)) {
          fd1.type = "boolean";
          fd1.clType = FieldDesc.BOOL;
        } else {
          if (etRoot instanceof XSComplexTypeDefinition) {
            final XSComplexTypeDefinition ct = (XSComplexTypeDefinition) etRoot;
            // XXX xerces2.7.1 wierd annotation inheritance bug fix
            //ann = (XSAnnotation) (ct.getAnnotations()!=null && ct.getAnnotations().getLength()>0?ct.getAnnotations().item(0):null);
            ann = (XSAnnotation) (ct.getAnnotations() != null && ct.getAnnotations().getLength() > 0 ? ct.getAnnotations().item(ct.getAnnotations().getLength() - 1) : null);
            documentation = parseAnnotationString("Type " + ct.getNamespace() + ":" + ct.getName() + " documentation", ann != null ? ann.getAnnotationString() : null);
            if (documentation != null) {
              fd1.documentation = fd1.documentation != null ? fd1.documentation + "\n" + documentation : documentation;
            }
          }
          fd1.simpleTypesString = getSimpleTypesString(etRoot);

          // "fully-qualified-classType", "jndi-nameType", "transaction-typeType"
          // "java-identifierType", "pathType"
          fd1.type = et.getName();
          if (fd1.type == null) {
            fd1.type = "String";
            fd1.def = "null";
            fd1.clType = FieldDesc.STR;
//            fd1.type = "boolean";
//            fd1.def = "false";
//            fd1.clType = FieldDesc.BOOL;
          } else if (fd1.type.equals("string") || fd1.type.equals("anyURI")) {
            fd1.type = "String";
          } else if (fd1.type.equals("boolean")) {
            fd1.type = "String";
          } else if (fd1.type.equals("emptyType")) {
            fd1.type = "boolean";
            fd1.def = "false";
            fd1.clType = FieldDesc.BOOL;
          } else if (fd1.type.equals("decimal")) {
            fd1.type = "String";
            fd1.def = "\"0.0\"";
          } else if (fd1.type.equals("QName")) {
            fd1.type = "String";
          } else if (fd1.type.equals("extensibleType")) {
            fd1.type = "Object";
          } else {
            if (et.getBaseType() != null &&
                ("anySimpleType".equals(et.getBaseType().getName())
              || "anyType".equals(et.getBaseType().getName()))) {
              fd1.type = "String";
              fd1.def = "null";
              fd1.clType = FieldDesc.STR;
            } else {
              fd1.type = "boolean";
              fd1.def = "false";
              fd1.clType = FieldDesc.BOOL;
            }
            Util.logwarn("using '" + fd1.type + "' for unknown base type: " + et.getName() + " for " + el);
          }
        }
        if ((pentry.many || p.getMaxOccursUnbounded() || p.getMaxOccurs() > 1) && fd1.clType != FieldDesc.BOOL) {
          fd1.elementType = fd1.type;
          fd1.elementName = fd1.name;
          fd1.type = "List<" + fd1.elementType + ">";
          fd1.name = Util.pluralize(fd1.name);
          fd1.def = "new ArrayList(0)";
          fd1.clType = -fd1.clType;
          fd1.comment = "array of " + fd1.elementType;
        }
        fd1.realIndex = td.fdMap.size();
        boolean merge = globalMerge || globalChoice.containsKey(p) && globalChoice.values().contains(fd1.name);
        td.duplicates = Util.addToNameMap(td.fdMap, fd1, merge) || td.duplicates;
        globalChoice.put(p, fd1.name);
      } else if (p.getTerm() instanceof XSModelGroup) {
        boolean addToGlobalChoice = false;
        boolean many = p.getMaxOccursUnbounded() || p.getMaxOccurs() > 1;
        XSObjectList l = ((XSModelGroup) p.getTerm()).getParticles();
        if (!many) {
          if (((XSModelGroup) p.getTerm()).getCompositor() == XSModelGroup.COMPOSITOR_CHOICE) {
            addToGlobalChoice = true;
            choiceList.add(l);
          } else {
            // generate group interface???
            XSModelGroup groupDef = (XSModelGroup) p.getTerm();
            TypeDesc gtd = processGroup(groupDef, models, jtMap, nsdMap);
            if (gtd != null) supers.add(gtd);
          }
        }
        if (globalChoice.containsKey(p)) {
          addToGlobalChoice = true;
        }
        for (int i = 0; i < l.getLength(); i++) {
          final PEntry o = new PEntry((XSParticle) l.item(i), many);
          plist.add(o);
          if (addToGlobalChoice && !globalChoice.containsKey(o.p)) {
            globalChoice.put(o.p, null);
          }
        }
      }
    }
    int i = 0;
    for (Iterator<FieldDesc> it = td.fdMap.values().iterator(); it.hasNext(); i++) {
      FieldDesc fd = it.next();
      fd.idx = i;
    }
    for (XSObjectList l : choiceList) {
      final ArrayList<XSParticle> clist = new ArrayList<>();
      final LinkedList<XSParticle> elist = new LinkedList<>();
      for (i = 0; i < l.getLength(); i++) {
        elist.add((XSParticle) l.item(i));
      }
      while (!elist.isEmpty()) {
        final XSParticle p = elist.removeFirst();
        if (p.getTerm() instanceof XSModelGroup) {
          XSObjectList l2 = ((XSModelGroup) p.getTerm()).getParticles();
          for (int i2 = 0; i2 < l2.getLength(); i2++) {
            elist.addFirst((XSParticle) l2.item(i2));
          }
        } else if (p.getTerm() instanceof XSElementDecl) {
          clist.add(p);
        }
      }
      boolean choiceOpt = true;
      FieldDesc[] choice = new FieldDesc[clist.size()];
      for (i = 0; i < choice.length; i++) {
        XSParticle p = clist.get(i);
        XSElementDecl el = (XSElementDecl) p.getTerm();
        String s = Util.toJavaFieldName(el.getName());
        if (p.getMaxOccursUnbounded() || p.getMaxOccurs() > 1) {
          s = Util.pluralize(s);
        }
        FieldDesc fd = td.fdMap.get(s);
        if (fd == null) {
          fd = td.fdMap.get(Util.pluralize(s));
          if (fd == null) {
            Util.logerr("uknown choice element: " + s);
          }
        }

        if (fd != null) {
          choice[i] = fd;
          choice[i].choice = choice;
          if (fd.required) choiceOpt = false;
        }
      }
      for (i = 0; i < choice.length; i++) {
        if (choice[i] != null) {
          choice[i].choiceOpt = choiceOpt;
        }
      }
    }
    td.supers = supers.toArray(new TypeDesc[supers.size()]);
  }
  public static String parseAnnotationString(String title, String str) {
    if (str == null) return null;
    int idx = str.indexOf(":documentation");
    if (idx == -1) idx = str.indexOf("<documentation");
    if (idx == -1) return null;
    idx = str.indexOf(">", idx + 1);
    if (idx == -1) return null;
    int idx2 = str.indexOf(":documentation", idx + 1);
    if (idx2 == -1) idx2 = str.indexOf("</documentation", idx + 1);
    idx2 = str.lastIndexOf("<", idx2 + 1);
    str = str.substring(idx + 1, idx2).trim();

    idx = str.indexOf("<![CDATA[");
    if (idx > -1) {
      idx = str.indexOf("[", idx + 3);
      idx2 = str.indexOf("]]>", idx + 1);
      str = str.substring(idx + 1, idx2);
    }
    return "<pre>\n<h3>" + title + "</h3>\n" + str + "\n</pre>";
  }

  public String toJavaTypeName(XSObject xs, Map<String, NamespaceDesc> nsdMap) {
    String name = xs.getName();
    if (name == null) {
      if (xs instanceof TypeInfo) {
        name = ((TypeInfo) xs).getTypeName();
        if (name != null && name.startsWith("#")) {
          name = name.substring(1);
        }
      }
    }
    return model.toJavaTypeName(name, xs.getNamespace());
  }

  public static class PEntry {
    public PEntry(XSParticle p, boolean many) {
      this.p = p;
      this.many = many;
    }

    XSParticle p;
    boolean many;
  }

}
