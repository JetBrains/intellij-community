// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.net.ssl;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.AnActionButtonRunnable;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.UIBundle;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreeSelectionModel;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.intellij.util.net.ssl.CertificateUtil.getCommonName;
import static com.intellij.util.net.ssl.ConfirmingTrustManager.MutableTrustManager;

@ApiStatus.Internal
public final class CertificateConfigurable implements SearchableConfigurable, Configurable.NoScroll, CertificateListener {

  public static final FileChooserDescriptor CERTIFICATE_DESCRIPTOR = FileChooserDescriptorFactory.createSingleFileDescriptor()
    .withTitle(IdeBundle.message("settings.certificate.choose.certificate"))
    .withExtensionFilter(IdeBundle.message("settings.certificate.filter.label"), "crt", "cer", "pem", "der");

  private final Map<String, JComponent> certificatePanels = new HashMap<>();

  private CertificateConfigurableUi ui;

  private MutableTrustManager myTrustManager;

  private CertificateTreeBuilder myTreeBuilder;
  private final Set<X509Certificate> myCertificates = new HashSet<>();

  @Override
  public @NotNull JComponent createComponent() {
    Tree tree = new Tree();
    myTreeBuilder = new CertificateTreeBuilder(tree);

    myTrustManager = CertificateManager.getInstance().getCustomTrustManager();
    // show newly added certificates
    myTrustManager.addListener(this);

    tree.getEmptyText().setText(IdeBundle.message("settings.certificate.no.certificates"));
    tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    tree.setRootVisible(false);

    ToolbarDecorator treeDecorator = ToolbarDecorator.createDecorator(tree).disableUpDownActions();
    treeDecorator.setAddAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        // show choose file dialog, add certificate
        FileChooser.chooseFile(CERTIFICATE_DESCRIPTOR, null, null, file -> {
          String path = file.getPath();
          X509Certificate certificate = CertificateUtil.loadX509Certificate(path);
          if (certificate == null) {
            Messages.showErrorDialog(ui.panel, IdeBundle.message("settings.certificate.malformed.x509.server.certificate"),
                                     IdeBundle.message("settings.certificate.not.imported"));
          }
          else if (myCertificates.contains(certificate)) {
            Messages.showWarningDialog(ui.panel, IdeBundle.message("settings.certificate.certificate.already.exists"),
                                       IdeBundle.message("settings.certificate.not.imported"));
          }
          else {
            myCertificates.add(certificate);
            myTreeBuilder.addCertificate(certificate);
            addCertificatePanel(certificate);
            myTreeBuilder.selectCertificate(certificate);
          }
        });
      }
    }).setRemoveAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        // allow to delete several certificates at once
        for (X509Certificate certificate : myTreeBuilder.getSelectedCertificates(true)) {
          myCertificates.remove(certificate);
          myTreeBuilder.removeCertificate(certificate);
        }
        if (myCertificates.isEmpty()) {
          ui.setDetails(null);
        }
        else {
          myTreeBuilder.selectFirstCertificate();
        }
      }
    });

    tree.addTreeSelectionListener(new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent e) {
        X509Certificate certificate = myTreeBuilder.getFirstSelectedCertificate(true);
        if (certificate != null) {
          ui.setDetails(certificatePanels.get(getCardName(certificate)));
        }
      }
    });

    TreeUtil.expandAll(tree);
    ui = new CertificateConfigurableUi(treeDecorator);

    return ui.panel;
  }

  private void addCertificatePanel(@NotNull X509Certificate certificate) {
    JPanel infoPanel = new CertificateInfoPanel(certificate);
    UIUtil.addInsets(infoPanel, UIUtil.PANEL_REGULAR_INSETS);
    certificatePanels.put(getCardName(certificate), new JBScrollPane(infoPanel));
  }

  private static String getCardName(@NotNull X509Certificate certificate) {
    return certificate.getSubjectX500Principal().getName();
  }

  @Override
  public @NotNull String getId() {
    return "http.certificates";
  }

  @Override
  public @Nls String getDisplayName() {
    return UIBundle.message("configurable.CertificateConfigurable.display.name");
  }

  @Override
  public String getHelpTopic() {
    return "reference.idesettings.server.certificates";
  }

  @Override
  public boolean isModified() {
    return (ui != null && ui.panel.isModified()) ||
           !myCertificates.equals(new HashSet<>(myTrustManager.getCertificates()));
  }

  @Override
  public void apply() throws ConfigurationException {
    List<X509Certificate> existing = myTrustManager.getCertificates();

    Set<X509Certificate> added = new HashSet<>(myCertificates);
    added.removeAll(existing);

    Set<X509Certificate> removed = new HashSet<>(existing);
    removed.removeAll(myCertificates);

    for (X509Certificate certificate : added) {
      if (!myTrustManager.addCertificate(certificate)) {
        throw new ConfigurationException(IdeBundle.message("settings.certificate.cannot.add.certificate.for", getCommonName(certificate)),
                                         IdeBundle.message("settings.certificate.cannot.add.certificate"));
      }
    }

    for (X509Certificate certificate : removed) {
      if (!myTrustManager.removeCertificate(certificate)) {
        throw new ConfigurationException(
          IdeBundle.message("settings.certificate.cannot.remove.certificate.for", getCommonName(certificate)),
          IdeBundle.message("settings.certificate.cannot.remove.certificate"));
      }
    }

    ui.panel.apply();
  }

  @Override
  public void reset() {
    List<X509Certificate> original = myTrustManager.getCertificates();
    myTreeBuilder.reset(original);

    myCertificates.clear();
    myCertificates.addAll(original);

    certificatePanels.clear();
    ui.setDetails(null);

    // fill lower panel with cards
    for (X509Certificate certificate : original) {
      addCertificatePanel(certificate);
    }

    if (!myCertificates.isEmpty()) {
      myTreeBuilder.selectFirstCertificate();
    }

    ui.panel.reset();
  }

  @Override
  public void disposeUIResources() {
    Disposer.dispose(myTreeBuilder);
    myTrustManager.removeListener(this);

    ui = null;
    certificatePanels.clear();
  }

  @Override
  public void certificateAdded(final X509Certificate certificate) {
    UIUtil.invokeLaterIfNeeded(() -> {
      if (myTreeBuilder != null && !myCertificates.contains(certificate)) {
        myCertificates.add(certificate);
        myTreeBuilder.addCertificate(certificate);
        addCertificatePanel(certificate);
      }
    });
  }

  @Override
  public void certificateRemoved(final X509Certificate certificate) {
    UIUtil.invokeLaterIfNeeded(() -> {
      if (myTreeBuilder != null && myCertificates.contains(certificate)) {
        myCertificates.remove(certificate);
        myTreeBuilder.removeCertificate(certificate);
      }
    });
  }
}
