package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.AntElementRole;
import com.intellij.lang.ant.psi.*;
import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.ReferenceType;
import com.intellij.psi.impl.source.resolve.reference.impl.GenericReference;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.StringBuilderSpinAllocator;
import org.apache.tools.ant.taskdefs.Property;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class AntProjectImpl extends AntStructuredElementImpl implements AntProject {
  private AntTarget[] myTargets;
  private AntTarget[] myImportedTargets;
  private AntFile[] myImports;
  private List<AntProperty> myPredefinedProps = new ArrayList<AntProperty>();
  @NonNls private List<String> myEnvPrefixes;
  @NonNls private static final String myDefaultEnvPrefix = "env.";

  public AntProjectImpl(final AntFileImpl parent, final XmlTag tag, final AntTypeDefinition projectDefinition) {
    super(parent, tag);
    myDefinition = projectDefinition;
  }

  @NonNls
  public String toString() {
    final @NonNls StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      builder.append("AntProject[");
      final String name = getName();
      builder.append((name == null) ? "unnamed" : name);
      builder.append("]");
      if (getDescription() != null) {
        builder.append(" :");
        builder.append(getDescription());
      }
      return builder.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }

  public AntElementRole getRole() {
    return AntElementRole.PROJECT_ROLE;
  }

  public synchronized void clearCaches() {
    super.clearCaches();
    myTargets = null;
    myImports = null;
    myEnvPrefixes = null;
  }

  @Nullable
  public String getBaseDir() {
    return getSourceElement().getAttributeValue("basedir");
  }

  @Nullable
  public String getDescription() {
    final XmlTag tag = getSourceElement().findFirstSubTag("description");
    return tag != null ? tag.getValue().getTrimmedText() : null;
  }

  @NotNull
  public synchronized AntTarget[] getTargets() {
    if (myTargets != null) return myTargets;
    final List<AntTarget> targets = new ArrayList<AntTarget>();
    for (final AntElement child : getChildren()) {
      if (child instanceof AntTarget) targets.add((AntTarget)child);
    }
    final int size = targets.size();
    return myTargets = (size == 0) ? AntTarget.EMPTY_TARGETS : targets.toArray(new AntTarget[size]);
  }

  @Nullable
  public AntTarget getDefaultTarget() {
    for (final PsiReference ref : getReferences()) {
      final GenericReference reference = (GenericReference)ref;
      if (reference.getType().isAssignableTo(ReferenceType.ANT_TARGET)) {
        return (AntTarget)reference.resolve();
      }
    }
    return null;
  }

  @NotNull
  public AntTarget[] getImportTargets() {
    if (myImportedTargets == null) {
      final AntFile[] importedFiles = getImportedFiles();
      if (importedFiles.length == 0) {
        myImportedTargets = AntTarget.EMPTY_TARGETS;
      }
      else {
        final List<AntTarget> targets = new ArrayList<AntTarget>();
        for (final AntFile imported : importedFiles) {
          final AntProject project = imported.getAntProject();
          if (project != null) {
            for (final AntTarget target : project.getTargets()) {
              targets.add(target);
            }
          }
        }
        final int size = targets.size();
        myImportedTargets = (size == 0) ? AntTarget.EMPTY_TARGETS : targets.toArray(new AntTarget[size]);
      }
    }
    return myImportedTargets;
  }

  @Nullable
  public synchronized AntTarget getTarget(final String name) {
    for (final AntTarget target : getTargets()) {
      if (name.equals(target.getName())) {
        return target;
      }
    }
    return null;
  }

  @NotNull
  public synchronized AntFile[] getImportedFiles() {
    if (myImports == null) {
      // this is necessary to avoid recurrent getImportedFiles() and stack overflow
      myImports = AntFile.NO_FILES;
      final List<AntFile> imports = new ArrayList<AntFile>();
      for (final XmlTag tag : getSourceElement().getSubTags()) {
        if ("import".equals(tag.getName())) {
          final AntFile imported = AntImportImpl.getImportedFile(tag.getAttributeValue("file"), this);
          if (imported != null) {
            imports.add(imported);
          }
        }
      }
      final int importedFiles = imports.size();
      if (importedFiles > 0) {
        myImports = imports.toArray(new AntFile[importedFiles]);
      }
    }
    return myImports;
  }

  public synchronized void addEnvironmentPropertyPrefix(@NotNull final String envPrefix) {
    checkEnvList();
    final String env = (envPrefix.endsWith(".")) ? envPrefix : envPrefix + '.';
    if (myEnvPrefixes.indexOf(env) < 0) {
      myEnvPrefixes.add(env);
      for (AntProperty element : getProperties()) {
        final String name = element.getName();
        if (name != null && name.startsWith(myDefaultEnvPrefix)) {
          setProperty(env + name.substring(myDefaultEnvPrefix.length()), element);
        }
      }
    }
  }

  public synchronized boolean isEnvironmentProperty(@NotNull final String propName) {
    checkEnvList();
    for (final String prefix : myEnvPrefixes) {
      if (propName.startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  public AntProperty getProperty(final String name) {
    if (getParent() == null) return null;
    checkPropertiesMap();
    return super.getProperty(name);
  }

  public void setProperty(final String name, final AntProperty element) {
    if (getParent() != null) {
      checkPropertiesMap();
      super.setProperty(name, element);
    }
  }

  @NotNull
  public AntProperty[] getProperties() {
    if (getParent() == null) return AntProperty.EMPTY_ARRAY;
    checkPropertiesMap();
    return super.getProperties();
  }

  @SuppressWarnings({"UseOfObsoleteCollectionType"})
  void loadPredefinedProperties(final Hashtable properties, final Map<String, String> externalProps) {
    final Enumeration props = (properties != null) ? properties.keys() : (new Hashtable()).keys();
    @NonNls final StringBuilder builder = StringBuilderSpinAllocator.alloc();
    builder.append("<project name=\"fake\">");
    try {
      while (props.hasMoreElements()) {
        final String name = (String)props.nextElement();
        final String value = (String)properties.get(name);
        builder.append("<property name=\"");
        builder.append(name);
        builder.append("\" value=\"");
        builder.append(value);
        builder.append("\"/>");
      }
      final Map<String, String> envMap = System.getenv();
      for (final String name : envMap.keySet()) {
        final String value = envMap.get(name);
        builder.append("<property name=\"");
        builder.append(name);
        builder.append("\" value=\"");
        builder.append(value);
        builder.append("\"/>");
      }
      if (externalProps != null) {
        for (final String name : externalProps.keySet()) {
          final String value = externalProps.get(name);
          builder.append("<property name=\"");
          builder.append(name);
          builder.append("\" value=\"");
          builder.append(value);
          builder.append("\"/>");
        }
      }
      final VirtualFile file = getContainingFile().getVirtualFile();
      String basedir = getBaseDir();
      if (file != null && (basedir == null || ".".equals(basedir))) {
        final VirtualFile dir = file.getParent();
        if (dir != null) {
          basedir = dir.getPath();
        }
      }
      builder.append("<property name=\"basedir\" value=\"");
      builder.append(basedir);
      builder.append("\"/>");
      builder.append("<property name=\"ant.home\" value=\"\"/>");
      builder.append("<property name=\"ant.version\" value=\"1.6.5\"/>");
      builder.append("<property name=\"ant.project.name\" value=\"");
      final String name = getName();
      builder.append((name == null) ? "" : name);
      builder.append("\"/>");
      builder.append("<property name=\"ant.java.version\" value=\"");
      builder.append(SystemInfo.JAVA_VERSION);
      builder.append("\"/>");
      if (file != null) {
        final String path = file.getPath();
        builder.append("<property name=\"ant.file\" value=\"");
        builder.append(path);
        builder.append("\"/>");
        if (name != null) {
          builder.append("<property name=\"ant.file.");
          builder.append(name);
          builder.append("\" value=\"${ant.file}\"/>");
        }
      }
      builder.append("</project>");
      final PsiElementFactory elementFactory = getManager().getElementFactory();
      final XmlFile xmlFile = (XmlFile)elementFactory.createFileFromText("dummy.xml", StdFileTypes.XML, builder, LocalTimeCounter.currentTime(), false, false);
      final XmlDocument document = xmlFile.getDocument();
      if (document == null) return;
      final XmlTag rootTag = document.getRootTag();
      if (rootTag == null) return;
      final AntTypeDefinition propertyDef = getAntFile().getBaseTypeDefinition(Property.class.getName());
      final AntProject fakeProject = new AntProjectImpl(null, rootTag, myDefinition);
      for (final XmlTag tag : rootTag.getSubTags()) {
        final AntPropertyImpl property = new AntPropertyImpl(fakeProject, tag, propertyDef) {
          public PsiFile getContainingFile() {
            return getSourceElement().getContainingFile();
          }
        };
        myPredefinedProps.add(property);
      }
    }
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
    setPredefinedProperties();
  }

  private void setPredefinedProperties() {
    for (final AntProperty property : myPredefinedProps) {
      setProperty(property.getName(), property);
    }
  }

  private void checkPropertiesMap() {
    if (myProperties == null) {
      myProperties = new HashMap<String, AntProperty>(myPredefinedProps.size());
      setPredefinedProperties();
    }
  }

  private synchronized void checkEnvList() {
    if (myEnvPrefixes == null) {
      myEnvPrefixes = new ArrayList<String>();
      myEnvPrefixes.add(myDefaultEnvPrefix);
    }
  }

  protected AntElement[] getChildrenInner() {
    if (!myInGettingChildren) {
      final AntElement[] children = super.getChildrenInner();
      fixUndefinedElements(this, children);
      return children;
    }
    return AntElement.EMPTY_ARRAY;
  }

  private static void fixUndefinedElements(final AntElement parent, final AntElement[] elements) {
    for (int i = 0; i < elements.length; i++) {
      AntElement element = elements[i];
      if (element instanceof AntStructuredElement && ((AntStructuredElement)element).getTypeDefinition() == null) {
        element = AntElementFactory.createAntElement(parent, element.getSourceElement());
        if (element != null) {
          elements[i] = element;
        }
      }
      if (element != null) {
        fixUndefinedElements(element, (AntElement[])element.getChildren());
      }
    }
  }
}
