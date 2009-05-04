package com.intellij.appengine.server.instance;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.facet.pointers.FacetPointer;
import com.intellij.facet.pointers.FacetPointersManager;
import com.intellij.javaee.deployment.DeploymentProvider;
import com.intellij.javaee.run.configuration.CommonModel;
import com.intellij.javaee.run.configuration.ServerModel;
import com.intellij.javaee.run.execution.DefaultOutputProcessor;
import com.intellij.javaee.run.execution.OutputProcessor;
import com.intellij.javaee.serverInstances.J2EEServerInstance;
import com.intellij.javaee.web.facet.WebFacet;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.annotations.Tag;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class AppEngineServerModel implements ServerModel {
  private FacetPointer<WebFacet> myWebFacetPointer;
  private int myPort = 8080;
  private CommonModel myCommonModel;

  public J2EEServerInstance createServerInstance() throws ExecutionException {
    return new AppEngineServerInstance(myCommonModel);
  }

  public DeploymentProvider getDeploymentProvider() {
    return null;
  }

  public String getDefaultUrlForBrowser() {
    return "http://" + myCommonModel.getHost() + ":" + myPort;
  }

  public SettingsEditor<CommonModel> getEditor() {
    return new AppEngineRunConfigurationEditor(myCommonModel.getProject());
  }

  public OutputProcessor createOutputProcessor(ProcessHandler processHandler, J2EEServerInstance serverInstance) {
    return new DefaultOutputProcessor(processHandler);
  }

  public List<Pair<String, Integer>> getAddressesToCheck() {
    return Collections.singletonList(Pair.create(myCommonModel.getHost(), myPort));
  }

  public void checkConfiguration() throws RuntimeConfigurationException {
    if (myWebFacetPointer == null || myWebFacetPointer.getFacet() == null) {
      throw new RuntimeConfigurationError("Web Facet isn't specified");
    }
  }

  public int getDefaultPort() {
    return 8080;
  }

  public void setCommonModel(CommonModel commonModel) {
    myCommonModel = commonModel;
  }

  public Object clone() throws CloneNotSupportedException {
    return super.clone();
  }

  public int getLocalPort() {
    return myPort;
  }

  public void readExternal(Element element) throws InvalidDataException {
    final AppEngineModelSettings settings = new AppEngineModelSettings();
    XmlSerializer.deserializeInto(settings, element);
    myPort = settings.getPort();
    if (settings.myWebFacet != null) {
      myWebFacetPointer = FacetPointersManager.getInstance(myCommonModel.getProject()).create(settings.getWebFacet());
    }
    else {
      myWebFacetPointer = null;
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    XmlSerializer.serializeInto(new AppEngineModelSettings(myPort, myWebFacetPointer), element, new SkipDefaultValuesSerializationFilters());
  }

  @Nullable
  public WebFacet getWebFacet() {
    return myWebFacetPointer != null ? myWebFacetPointer.getFacet() : null;
  }

  public void setPort(int port) {
    myPort = port;
  }

  public void setWebFacet(@Nullable WebFacet webFacet) {
    if (webFacet != null) {
      myWebFacetPointer = FacetPointersManager.getInstance(myCommonModel.getProject()).create(webFacet);
    }
    else {
      myWebFacetPointer = null;
    }
  }

  public static class AppEngineModelSettings {
    @Tag("port")
    private int myPort = 8080;
    @Tag("web-facet")
    private String myWebFacet;

    public AppEngineModelSettings() {
    }

    public AppEngineModelSettings(int port, FacetPointer<WebFacet> pointer) {
      myPort = port;
      myWebFacet = pointer != null ? pointer.getId() : null;
    }

    public int getPort() {
      return myPort;
    }

    public void setPort(int port) {
      myPort = port;
    }

    public String getWebFacet() {
      return myWebFacet;
    }

    public void setWebFacet(String webFacet) {
      myWebFacet = webFacet;
    }
  }
}
