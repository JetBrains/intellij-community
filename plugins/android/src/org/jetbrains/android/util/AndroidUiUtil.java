package org.jetbrains.android.util;

import com.intellij.CommonBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.compiler.artifact.ApkSigningSettingsForm;
import org.jetbrains.android.compiler.artifact.ChooseKeyDialog;
import org.jetbrains.android.compiler.artifact.NewKeyStoreDialog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidUiUtil {
  private static final Logger LOG = Logger.getInstance("org.jetbrains.android.util.AndroidUiUtil");

  private AndroidUiUtil() {
  }

  @Nullable
  private static List<String> loadExistingKeys(@NotNull ApkSigningSettingsForm form) {
    final String errorPrefix = "Cannot load key store: ";
    InputStream is = null;
    try {
      //noinspection IOResourceOpenedButNotSafelyClosed
      is = new FileInputStream(new File(form.getKeyStorePathField().getText().trim()));
      final KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
      keyStore.load(is, form.getKeyStorePasswordField().getPassword());
      return AndroidUtils.toList(keyStore.aliases());
    }
    catch (KeyStoreException e) {
      Messages.showErrorDialog(form.getPanel(), errorPrefix + e.getMessage(), CommonBundle.getErrorTitle());
      return null;
    }
    catch (FileNotFoundException e) {
      Messages.showErrorDialog(form.getPanel(), errorPrefix + e.getMessage(), CommonBundle.getErrorTitle());
      return null;
    }
    catch (CertificateException e) {
      Messages.showErrorDialog(form.getPanel(), errorPrefix + e.getMessage(), CommonBundle.getErrorTitle());
      return null;
    }
    catch (NoSuchAlgorithmException e) {
      Messages.showErrorDialog(form.getPanel(), errorPrefix + e.getMessage(), CommonBundle.getErrorTitle());
      return null;
    }
    catch (IOException e) {
      Messages.showErrorDialog(form.getPanel(), errorPrefix + e.getMessage(), CommonBundle.getErrorTitle());
      return null;
    }
    finally {
      if (is != null) {
        try {
          is.close();
        }
        catch (IOException e) {
          LOG.info(e);
        }
      }
    }
  }

  public static void initSigningSettingsForm(@NotNull final Project project, @NotNull final ApkSigningSettingsForm form) {
    form.getLoadKeyStoreButton().addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final String defaultPath = form.getKeyStorePathField().getText().trim();
        final VirtualFile defaultFile = LocalFileSystem.getInstance().findFileByPath(defaultPath);
        final FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor();
        final VirtualFile file = FileChooser.chooseFile(descriptor, form.getPanel(), project, defaultFile);
        if (file != null) {
          form.getKeyStorePathField().setText(FileUtil.toSystemDependentName(file.getPath()));
        }
      }
    });

    form.getCreateKeyStoreButton().addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final NewKeyStoreDialog dialog = new NewKeyStoreDialog(project, form.getKeyStorePathField().getText());
        dialog.show();

        if (dialog.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
          form.getKeyStorePathField().setText(dialog.getKeyStorePath());
          form.getKeyStorePasswordField().setText(String.valueOf(dialog.getKeyStorePassword()));
          form.getKeyAliasField().setText(dialog.getKeyAlias());
          form.getKeyPasswordField().setText(String.valueOf(dialog.getKeyPassword()));
        }
      }
    });

    form.getKeyAliasField().getButton().addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final List<String> keys = loadExistingKeys(form);
        if (keys == null) {
          return;
        }
        final ChooseKeyDialog dialog =
          new ChooseKeyDialog(project, form.getKeyStorePathField().getText().trim(), form.getKeyStorePasswordField().getPassword(), keys,
                              form.getKeyAliasField().getText().trim());
        dialog.show();

        if (dialog.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
          final String chosenKey = dialog.getChosenKey();
          if (chosenKey != null) {
            form.getKeyAliasField().setText(chosenKey);
          }

          final char[] password = dialog.getChosenKeyPassword();
          if (password != null) {
            form.getKeyPasswordField().setText(String.valueOf(password));
          }
        }
      }
    });
  }
}
