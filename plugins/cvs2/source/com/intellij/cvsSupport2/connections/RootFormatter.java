package com.intellij.cvsSupport2.connections;

import com.intellij.cvsSupport2.config.CvsRootConfiguration;
import com.intellij.cvsSupport2.config.ProxySettings;
import com.intellij.cvsSupport2.connections.ext.ExtConnectionCvsSettings;
import com.intellij.cvsSupport2.connections.local.LocalConnectionSettings;
import com.intellij.cvsSupport2.connections.pserver.PServerCvsSettings;
import com.intellij.cvsSupport2.connections.pserver.PServerLoginProvider;
import com.intellij.cvsSupport2.connections.ssh.SshConnectionSettings;
import com.intellij.openapi.util.text.StringUtil;
import org.netbeans.lib.cvsclient.connection.PServerPasswordScrambler;

import java.text.MessageFormat;

/**
 * author: lesya
 */

public class RootFormatter {

  public static CvsConnectionSettings createConfigurationOn(CvsRootConfiguration cvsRootConfiguration) {
    return createConfigurationOn(CvsRootParser.valueOf(cvsRootConfiguration.getCvsRootAsString(), true),
                                 cvsRootConfiguration);
  }

  public static CvsConnectionSettings createConfigurationOn(CvsRootParser root, CvsRootConfiguration cvsRoot) {
    if (root.METHOD.equals(CvsMethod.LOCAL_METHOD)) {
      return createLocalSettingsOn(root.REPOSITORY, cvsRoot);
    }
    else if (root.METHOD.equals(CvsMethod.PSERVER_METHOD)) {
      return createPServerSettingsOn(root, cvsRoot);
    }
    else if (root.METHOD.equals(CvsMethod.EXT_METHOD)) {
      return createExtConnectionSettings(root, cvsRoot);
    }
    else if (root.METHOD.equals(CvsMethod.SSH_METHOD)) {
      return createSshConnectionSettings(root, cvsRoot);
    }
    else {
      throw new RuntimeException(com.intellij.CvsBundle.message("exception.text.unsupported.method", root.METHOD));
    }
  }

  private static CvsConnectionSettings createLocalSettingsOn(String repository, CvsRootConfiguration cvsRootConfiguration) {
    LocalConnectionSettings settings = new LocalConnectionSettings(repository, cvsRootConfiguration);
    settings.REPOSITORY = repository;
    return settings;
  }

  private static CvsConnectionSettings createSshConnectionSettings(CvsRootParser root, CvsRootConfiguration cvsRoot) {
    CvsConnectionSettings result = (CvsConnectionSettings)fillSettings(root, new SshConnectionSettings(cvsRoot));
    result.METHOD = CvsMethod.SSH_METHOD;
    return result;
  }

  private static CvsConnectionSettings createExtConnectionSettings(CvsRootParser root, CvsRootConfiguration cvsRoot) {
    CvsConnectionSettings result = (CvsConnectionSettings)fillSettings(root, new ExtConnectionCvsSettings(cvsRoot));
    result.METHOD = CvsMethod.EXT_METHOD;
    return result;
  }

  private static CvsConnectionSettings createPServerSettingsOn(CvsRootParser root, CvsRootConfiguration cvsRoot) {
    CvsConnectionSettings result = (CvsConnectionSettings)fillSettings(root, new PServerCvsSettings(cvsRoot));
    if (root.PASSWORD != null) {
      result.PASSWORD = root.PASSWORD;
    } else {
      result.PASSWORD = getPServerConnectionPassword(cvsRoot.getCvsRootAsString(), root);
    }
    result.METHOD = CvsMethod.PSERVER_METHOD;
    return result;

  }

  private static CvsRootData fillSettings(CvsRootParser root, CvsRootData result) {
    String[] hostAndPort = root.HOST.split(":");

    result.HOST = hostAndPort[0];

    if (hostAndPort.length > 1) {
      result.PORT = Integer.parseInt(hostAndPort[1]);
    }
    else if (root.PORT != null) {
      try {
        result.PORT = Integer.parseInt(root.PORT);
      }
      catch (NumberFormatException e) {
        result.HOST = hostAndPort[0];
      }
    }
    else {
      result.HOST = hostAndPort[0];
    }

    String repository = root.REPOSITORY;
    if (StringUtil.startsWithChar(repository, ':')) repository = repository.substring(1);

    result.REPOSITORY = repository;

    String[] userAndPassword = root.USER_NAME.split(":");

    result.USER = userAndPassword[0];

    result.PROXY_HOST = root.PROXY_HOST;
    result.PROXY_PORT = root.PROXY_PORT;
    result.CONTAINS_PROXY_INFO = result.PROXY_HOST != null;

    if (result.PROXY_HOST != null) {
      if (result instanceof CvsConnectionSettings) {
        CvsConnectionSettings cvsConnectionSettings = ((CvsConnectionSettings)result);
        ProxySettings proxySettings = cvsConnectionSettings.getProxySettings();
        try {
          proxySettings.PROXY_PORT = Integer.parseInt(root.PROXY_PORT);
          proxySettings.USE_PROXY = true;
          proxySettings.PROXY_HOST = root.PROXY_HOST;
        }
        catch (NumberFormatException e) {
        }
      }
    }

    return result;
  }

  private static String getPServerConnectionPassword(String cvsRoot, CvsRootParser root) {
    String[] userAndPassword = root.USER_NAME.split(":");
    if (userAndPassword.length > 1) {
      return PServerPasswordScrambler.getInstance().scramble(userAndPassword[1]);
    }
    else {
      return PServerLoginProvider.getInstance().getScrambledPasswordForCvsRoot(cvsRoot);
    }
  }

  public static CvsRootData createSettingsOn(String cvsRoot, boolean check) {
    CvsRootParser cvsRootParser = CvsRootParser.valueOf(cvsRoot, check);
    CvsRootData result = new CvsRootData(cvsRoot);
    fillSettings(cvsRootParser, result);
    result.METHOD = cvsRootParser.METHOD;
    return result;
  }

}
