// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.cvsSupport2.config;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.DifferenceFilter;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.SystemProperties;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(name = "CvsApplicationLevelConfiguration", storages = @Storage(value = "other.xml", roamingType = RoamingType.DISABLED),
  reportStatistic = false)
public class CvsApplicationLevelConfiguration implements PersistentStateComponent<Element> {
  private static final Logger LOG = Logger.getInstance(CvsApplicationLevelConfiguration.class);

  public List<CvsRootConfiguration> CONFIGURATIONS = new ArrayList<>();

  public ExtConfiguration EXT_CONFIGURATION = new ExtConfiguration();
  public SshSettings SSH_CONFIGURATION = new SshSettings();
  public LocalSettings LOCAL_CONFIGURATION = new LocalSettings();
  public ProxySettings PROXY_SETTINGS = new ProxySettings();
  public SshSettings SSH_FOR_EXT_CONFIGURATION = new SshSettings();

  @NonNls private static final String CONFIGURATION_ELEMENT_NAME = "Configuration";
  public String PATH_TO_PASSWORD_FILE = null;
  public int TIMEOUT = 10;
  public boolean MAKE_CHECKED_OUT_FILES_READONLY = false;
  public boolean CHECKOUT_PRUNE_EMPTY_DIRECTORIES = true;
  public String CHECKOUT_KEYWORD_SUBSTITUTION = null;
  public boolean SHOW_RESTORE_DIRECTORIES_CONFIRMATION = true;

  @NotNull public String ENCODING;

  public boolean USE_GZIP = false;
  @NonNls public static final String DEFAULT = "Default";

  public boolean DO_OUTPUT = false;
  public boolean SEND_ENVIRONMENT_VARIABLES_TO_SERVER = false;
  public boolean SHOW_PATH = true;

  public CvsApplicationLevelConfiguration() {
    ENCODING = DEFAULT;
  }


  public static CvsApplicationLevelConfiguration getInstance() {
    return ServiceManager.getService(CvsApplicationLevelConfiguration.class);
  }

  @Nullable
  @Override
  public Element getState() {
    Element state = new Element("state");
    DefaultJDOMExternalizer.writeExternal(this, state, new DifferenceFilter<>(this, new CvsApplicationLevelConfiguration()));
    for (CvsRootConfiguration configuration : CONFIGURATIONS) {
      Element child = new Element(CONFIGURATION_ELEMENT_NAME);
      configuration.writeExternal(child);
      state.addContent(child);
    }
    return state;
  }

  @Override
  public void loadState(@NotNull Element state) {
    DefaultJDOMExternalizer.readExternal(this, state);
    for (Element child : state.getChildren(CONFIGURATION_ELEMENT_NAME)) {
      CONFIGURATIONS.add(createConfigurationOn(child));
    }

    if (!encodingExists(ENCODING)) {
      ENCODING = DEFAULT;
    }

    updateConfigurations();
  }

  private static boolean encodingExists(String encoding) {
    final Charset[] availableCharsets = CharsetToolkit.getAvailableCharsets();
    for (Charset availableCharset : availableCharsets) {
      if (availableCharset.name().equals(encoding)) {
        return true;
      }
    }
    return false;
  }

  private CvsRootConfiguration createConfigurationOn(Element child) {
    CvsRootConfiguration config = createNewConfiguration(this);
    config.readExternal(child);
    return config;
  }

  private boolean passFileExists() {
    if (PATH_TO_PASSWORD_FILE == null) return false;
    return new File(convertToIOFilePath(PATH_TO_PASSWORD_FILE)).isFile();
  }

  public static String convertToIOFilePath(String presentation) {
    String userHome = SystemProperties.getUserHome().replace(File.separatorChar, '/');
    presentation = presentation.replace(File.separatorChar, '/');
    try {
      String result = StringUtil.replace(presentation, "$userdir", userHome);
      result = result.replace('/', File.separatorChar);
      return result;
    }
    catch (Exception ex) {
      LOG.error("userHome = " + userHome + ", presentation = " + presentation);
      return "";
    }
  }

  public File getPassFile() {
    return new File(convertToIOFilePath(getPathToPassFilePresentation()));
  }

  public String getPathToPassFilePresentation() {
    if (!passFileExists()) {
      PATH_TO_PASSWORD_FILE = "$userdir" + "/.cvspass";
    }
    return PATH_TO_PASSWORD_FILE;
  }

  public CvsRootConfiguration getConfigurationForCvsRoot(String root) {
    for (CvsRootConfiguration cvsRootConfiguration : CONFIGURATIONS) {
      if (cvsRootConfiguration.getCvsRootAsString().equals(root)) {
        return cvsRootConfiguration;
      }
    }
    CvsRootConfiguration newConfig = createNewConfiguration(this);
    newConfig.CVS_ROOT = root;
    CONFIGURATIONS.add(newConfig);
    return newConfig;

  }


  @NotNull public static String getCharset() {
    String value = getInstance().ENCODING;
    if (DEFAULT.equals(value)) {
      return CharsetToolkit.getDefaultSystemCharset().name();
    } else {
      return value;
    }

  }

  public void setPathToPasswordFile(final String text) {
    PATH_TO_PASSWORD_FILE = text;
    updateConfigurations();
  }

  private void updateConfigurations() {
    final File passFile = getPassFile();
    if (!passFile.isFile()) {
      return;
    }
    try {
      BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(passFile), StandardCharsets.UTF_8));
      try {
        String line;
        while ((line = reader.readLine()) != null) {
          line = StringUtil.trimStart(line, "/1 ");
          final int sepPosition = line.indexOf(' ');
          if (sepPosition > 0) {
            final String cvsRoot = line.substring(0, sepPosition);
            tryToAddNewRoot(cvsRoot);
          }
        }
      } finally {
        reader.close();
      }
    } catch (IOException e) {
      //ignore
    }
  }

  private void tryToAddNewRoot(final String cvsRoot) {
    for (CvsRootConfiguration configuration : CONFIGURATIONS) {
      if (Objects.equals(configuration.getCvsRootAsString(), cvsRoot)) {
        return;
      }
    }

    final CvsRootConfiguration newConfiguration = createNewConfiguration(this);
    newConfiguration.CVS_ROOT = cvsRoot;
    CONFIGURATIONS.add(newConfiguration);
  }

  public static CvsRootConfiguration createNewConfiguration(CvsApplicationLevelConfiguration mainConfiguration) {
    final CvsRootConfiguration result = new CvsRootConfiguration();
    result.EXT_CONFIGURATION = mainConfiguration.EXT_CONFIGURATION.clone();
    result.SSH_CONFIGURATION = mainConfiguration.SSH_CONFIGURATION.clone();
    result.SSH_FOR_EXT_CONFIGURATION = mainConfiguration.SSH_FOR_EXT_CONFIGURATION.clone();
    result.LOCAL_CONFIGURATION = mainConfiguration.LOCAL_CONFIGURATION.clone();
    result.PROXY_SETTINGS = mainConfiguration.PROXY_SETTINGS.clone();
    return result;
  }
}
