package com.intellij.cvsSupport2.config;

import com.intellij.cvsSupport2.connections.ssh.ui.SshSettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.text.StringUtil;
import org.jdom.Element;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * author: lesya
 */
public class CvsApplicationLevelConfiguration implements ApplicationComponent, JDOMExternalizable {

  private static final Logger LOG = Logger.getInstance("#com.intellij.cvsSupport2.config.CvsApplicationLevelConfiguration");

  public List<CvsRootConfiguration> CONFIGURATIONS = new ArrayList<CvsRootConfiguration>();

  public ExtConfiguration EXT_CONFIGURATION = new ExtConfiguration();
  public SshSettings SSH_CONFIGURATION = new SshSettings();
  public LocalSettings LOCAL_CONFIGURATION = new LocalSettings();
  public ProxySettings PROXY_SETTINGS = new ProxySettings();
  public SshSettings SSH_FOR_EXT_CONFIGURATION = new SshSettings();

  private static final String CONFIGURATION_ELEMENT_NAME = "Configuration";
  public String PATH_TO_PASSWORD_FILE = null;
  public int TIMEOUT = 60;
  public boolean MAKE_CHECKED_OUT_FILES_READONLY = false;
  public boolean CHECKOUT_PRUNE_EMPTY_DIRECTORIES = true;
  public String CHECKOUT_KEYWORD_SUBSTITUTION = null;
  public boolean SHOW_RESTORE_DIRECTORIES_CONFIRMATION = true;
  public boolean USE_UTF8 = false;
  public boolean USE_GZIP = false;


  public static CvsApplicationLevelConfiguration getInstance() {
    return ApplicationManager.getApplication().getComponent(CvsApplicationLevelConfiguration.class);
  }

  public String getComponentName() {
    return "CvsApplicationLevelConfiguration";
  }

  public void initComponent() { }

  public void disposeComponent() {

  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
    for (Iterator each = element.getChildren(CONFIGURATION_ELEMENT_NAME).iterator(); each.hasNext();) {
      Element child = (Element)each.next();
      CONFIGURATIONS.add(createConfigurationOn(child));
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
    for (Iterator each = CONFIGURATIONS.iterator(); each.hasNext();) {
      createConfigurationElement((CvsRootConfiguration)each.next(), element);
    }
  }

  private void createConfigurationElement(CvsRootConfiguration configuration, Element element)
    throws WriteExternalException {
    Element child = new Element(CONFIGURATION_ELEMENT_NAME);
    configuration.writeExternal(child);
    element.addContent(child);
  }

  private CvsRootConfiguration createConfigurationOn(Element child) throws InvalidDataException {
    CvsRootConfiguration config = new CvsRootConfiguration(this);
    config.readExternal(child);
    return config;
  }

  private String defaultPathToPassFile() {
    return "$userdir" + "/.cvspass";
  }

  private boolean passFileExists() {
    if (PATH_TO_PASSWORD_FILE == null) return false;
    return new File(convertToIOFilePath(PATH_TO_PASSWORD_FILE)).isFile();
  }

  public static String convertToIOFilePath(String presentation) {
    String userHome = System.getProperty("user.home");
    userHome = userHome.replace(File.separatorChar, '/');
    presentation = presentation.replace(File.separatorChar, '/');
    try {
      String result = StringUtil.replace(presentation, "$userdir", userHome);
      result = result.replace('/', File.separatorChar);
      return result;
    }
    catch (Exception ex) {
      LOG.assertTrue(false, "userHome = " + userHome + ", presenation = " + presentation);
      return "";
    }
  }

  public String getPathToPassFile() {
    return convertToIOFilePath(getPathToPassFilePresentation());
  }

  public String getPathToPassFilePresentation() {
    if (!passFileExists()) {
      PATH_TO_PASSWORD_FILE = defaultPathToPassFile();
    }
    return PATH_TO_PASSWORD_FILE;
  }

  public CvsRootConfiguration getConfigurationForCvsRoot(String root) {
    for (Iterator each = CONFIGURATIONS.iterator(); each.hasNext();) {
      CvsRootConfiguration cvsRootConfiguration = (CvsRootConfiguration)each.next();
      if (cvsRootConfiguration.getCvsRootAsString().equals(root)) {
        return cvsRootConfiguration;
      }
    }
    CvsRootConfiguration newConfig = new CvsRootConfiguration(this);
    newConfig.CVS_ROOT = root;
    CONFIGURATIONS.add(newConfig);
    return newConfig;

  }


}
