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
package com.intellij.remoteServer.util;

import com.intellij.ide.DataManager;
import com.intellij.ide.actions.ShowSettingsUtilImpl;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.ex.SingleConfigurableEditor;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.remoteServer.ServerType;
import com.intellij.remoteServer.configuration.RemoteServer;
import com.intellij.remoteServer.configuration.RemoteServersManager;
import com.intellij.remoteServer.impl.configuration.SingleRemoteServerConfigurable;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.hash.HashMap;
import com.intellij.util.text.UniqueNameGenerator;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.Map;


public class CloudAccountSelectionEditor {

  private static final Map<ServerType<?>, Key<RemoteServer<?>>> ourCloudType2AccountKey
    = new HashMap<>();


  private JButton myNewButton;
  private ComboBox myAccountComboBox;
  private JPanel myMainPanel;

  private final List<ServerType<?>> myCloudTypes;

  private Runnable myServerSelectionListener;

  public CloudAccountSelectionEditor(List<ServerType<?>> cloudTypes) {
    myCloudTypes = cloudTypes;

    for (ServerType<?> cloudType : cloudTypes) {
      for (RemoteServer<?> account : RemoteServersManager.getInstance().getServers(cloudType)) {
        myAccountComboBox.addItem(new AccountItem(account));
      }
    }

    myNewButton.addActionListener(new ActionListener() {

      @Override
      public void actionPerformed(ActionEvent e) {
        onNewButton();
      }
    });

    myAccountComboBox.addActionListener(new ActionListener() {

      @Override
      public void actionPerformed(ActionEvent e) {
        if (myServerSelectionListener != null) {
          myServerSelectionListener.run();
        }
      }
    });
  }

  public void setAccountSelectionListener(Runnable listener) {
    myServerSelectionListener = listener;
  }

  private void onNewButton() {
    if (myCloudTypes.size() == 1) {
      createAccount(ContainerUtil.getFirstItem(myCloudTypes));
      return;
    }

    DefaultActionGroup group = new DefaultActionGroup();
    for (final ServerType<?> cloudType : myCloudTypes) {
      group.add(new AnAction(cloudType.getPresentableName(), cloudType.getPresentableName(), cloudType.getIcon()) {

        @Override
        public void actionPerformed(AnActionEvent e) {
          createAccount(cloudType);
        }
      });
    }
    JBPopupFactory.getInstance().createActionGroupPopup("New Account", group, DataManager.getInstance().getDataContext(myMainPanel),
                                                        JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, false)
      .showUnderneathOf(myNewButton);
  }

  private void createAccount(ServerType<?> cloudType) {
    RemoteServer<?> newAccount = RemoteServersManager.getInstance().createServer(cloudType, generateServerName(cloudType));

    final Ref<Consumer<String>> errorConsumerRef = new Ref<>();

    SingleRemoteServerConfigurable configurable = new SingleRemoteServerConfigurable(newAccount, null, true) {

      @Override
      protected void setConnectionStatusText(boolean error, String text) {
        super.setConnectionStatusText(error, error ? "" : text);
        errorConsumerRef.get().consume(error ? text : null);
      }
    };

    if (!new SingleConfigurableEditor(myMainPanel, configurable, ShowSettingsUtilImpl.createDimensionKey(configurable), false) {
      {
        errorConsumerRef.set(s -> setErrorText(s));
      }
    }.showAndGet()) {
      return;
    }

    newAccount.setName(configurable.getDisplayName());

    RemoteServersManager.getInstance().addServer(newAccount);
    AccountItem newAccountItem = new AccountItem(newAccount);
    myAccountComboBox.addItem(newAccountItem);
    myAccountComboBox.setSelectedItem(newAccountItem);
  }

  public JComponent getMainPanel() {
    return myMainPanel;
  }

  @Nullable
  public RemoteServer<?> getSelectedAccount() {
    AccountItem selectedItem = (AccountItem)myAccountComboBox.getSelectedItem();
    return selectedItem == null ? null : selectedItem.getAccount();
  }

  private static String generateServerName(ServerType<?> cloudType) {
    return UniqueNameGenerator.generateUniqueName(cloudType.getPresentableName(), s -> {
      for (RemoteServer<?> server : RemoteServersManager.getInstance().getServers()) {
        if (server.getName().equals(s)) {
          return false;
        }
      }
      return true;
    });
  }

  public void validate() throws ConfigurationException {
    if (getSelectedAccount() == null) {
      throw new ConfigurationException("Account required");
    }
  }

  public void setAccountOnContext(WizardContext context) {
    RemoteServer<?> account = getSelectedAccount();
    if (account == null) {
      return;
    }
    context.putUserData(getKey(account.getType()), account);
  }

  public static void unsetAccountOnContext(WizardContext context, ServerType<?> cloudType) {
    context.putUserData(getKey(cloudType), null);
  }

  private static Key<RemoteServer<?>> getKey(ServerType<?> cloudType) {
    Key<RemoteServer<?>> result = ourCloudType2AccountKey.get(cloudType);
    if (result == null) {
      result = new Key<>("cloud-account-" + cloudType.getId());
      ourCloudType2AccountKey.put(cloudType, result);
    }
    return result;
  }

  public static void createRunConfiguration(WizardContext context,
                                            ServerType<?> cloudType,
                                            Module module,
                                            CloudDeploymentNameConfiguration configuration) {
    RemoteServer<?> account = context.getUserData(getKey(cloudType));
    if (account == null) {
      return;
    }
    CloudRunConfigurationUtil.createRunConfiguration(account, module, configuration);
  }

  public void setSelectedAccount(String accountName) {
    for (int i = 0; i < myAccountComboBox.getItemCount(); i++) {
      AccountItem accountItem = (AccountItem)myAccountComboBox.getItemAt(i);
      if (StringUtil.equals(accountName, accountItem.getAccount().getName())) {
        myAccountComboBox.setSelectedItem(accountItem);
      }
    }
  }

  private static class AccountItem {

    private final RemoteServer<?> myAccount;

    public AccountItem(RemoteServer<?> account) {
      myAccount = account;
    }

    public RemoteServer<?> getAccount() {
      return myAccount;
    }

    @Override
    public String toString() {
      return myAccount.getName();
    }
  }
}
