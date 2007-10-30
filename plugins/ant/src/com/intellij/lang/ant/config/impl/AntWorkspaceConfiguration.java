package com.intellij.lang.ant.config.impl;

import com.intellij.lang.ant.config.AntBuildFile;
import com.intellij.lang.ant.config.AntBuildFileBase;
import com.intellij.lang.ant.config.AntConfiguration;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@State(
  name="antWorkspaceConfiguration",
  storages= {
    @Storage(
      id="other",
      file = "$WORKSPACE_FILE$"
    )}
)
public class AntWorkspaceConfiguration implements PersistentStateComponent<Element> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.lang.ant.config.impl.AntWorkspaceConfiguration");
  private final Project myProject;
  @NonNls private static final String BUILD_FILE = "buildFile";
  @NonNls private static final String URL = "url";
  private final AtomicReference<Element> myProperties = new AtomicReference<Element>(null);

  public boolean IS_AUTOSCROLL_TO_SOURCE;
  public boolean FILTER_TARGETS;

  public AntWorkspaceConfiguration(Project project) {
    myProject = project;
  }

  public Element getState() {
    try {
      final Element e = new Element("state");
      writeExternal(e);
      return e;
    }
    catch (WriteExternalException e1) {
      LOG.error(e1);
      return null;
    }
  }

  public void loadState(Element state) {
    try {
      readExternal(state);
    }
    catch (InvalidDataException e) {
      LOG.error(e);
    }
  }

  public void initComponent() {
  }

  public void readExternal(Element parentNode) throws InvalidDataException {
    loadGlobalSettings(parentNode);
    myProperties.set(parentNode);
  }

  public void writeExternal(Element parentNode) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, parentNode);
    for (final AntBuildFile buildFile : AntConfiguration.getInstance(myProject).getBuildFiles()) {
      Element element = new Element(BUILD_FILE);
      element.setAttribute(URL, buildFile.getVirtualFile().getUrl());
      ((AntBuildFileBase)buildFile).writeWorkspaceProperties(element);
      parentNode.addContent(element);
    }
  }

  public static AntWorkspaceConfiguration getInstance(Project project) {
    return ServiceManager.getService(project, AntWorkspaceConfiguration.class);
  }

  public void loadFileProperties() throws InvalidDataException {
    final Element properties = myProperties.getAndSet(null);
    if (properties == null) {
      return;
    }
    for (final AntBuildFile buildFile : AntConfiguration.getInstance(myProject).getBuildFiles()) {
      final Element fileElement = findChildByUrl(properties, buildFile.getVirtualFile().getUrl());
      if (fileElement == null) {
        continue;
      }
      ((AntBuildFileBase)buildFile).readWorkspaceProperties(fileElement);
    }
  }

  public void loadFromProjectSettings(Element parentNode) throws InvalidDataException {
    loadGlobalSettings(parentNode);
  }

  private void loadGlobalSettings(Element parentNode) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, parentNode);
  }

  @Nullable
  private static Element findChildByUrl(Element parentNode, String url) {
    List children = parentNode.getChildren(BUILD_FILE);
    for (final Object aChildren : children) {
      Element element = (Element)aChildren;
      if (Comparing.equal(element.getAttributeValue(URL), url)) return element;
    }
    return null;
  }
}
