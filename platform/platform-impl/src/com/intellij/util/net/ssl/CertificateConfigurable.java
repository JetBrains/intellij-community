// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.net.ssl;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileTypeDescriptor;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.*;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.*;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.security.cert.X509Certificate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.intellij.util.net.ssl.CertificateUtil.getCommonName;
import static com.intellij.util.net.ssl.ConfirmingTrustManager.MutableTrustManager;

/**
 * @author Mikhail Golubev
 */
@ApiStatus.Internal
public class CertificateConfigurable implements SearchableConfigurable, Configurable.NoScroll, CertificateListener {
  private static final FileTypeDescriptor CERTIFICATE_DESCRIPTOR =
    new FileTypeDescriptor(IdeBundle.message("settings.certificate.choose.certificate"),
                           ".crt", ".CRT",
                           ".cer", ".CER",
                           ".pem", ".PEM",
                           ".der", ".DER");
  @NonNls public static final String EMPTY_PANEL = "empty.panel";

  private JPanel myRootPanel;

  private JBCheckBox myAcceptAutomatically;

  private JPanel myCertificatesListPanel;
  private JPanel myDetailsPanel;
  private JPanel myEmptyPanel;
  private MutableTrustManager myTrustManager;

  private CertificateTreeBuilder myTreeBuilder;
  private final Set<X509Certificate> myCertificates = new HashSet<>();

  private void initializeUI() {
    Tree tree = new Tree();
    myTreeBuilder = new CertificateTreeBuilder(tree);

    myTrustManager = CertificateManager.getInstance().getCustomTrustManager();
    // show newly added certificates
    myTrustManager.addListener(this);

    tree.getEmptyText().setText(IdeBundle.message("settings.certificate.no.certificates"));
    tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    tree.setRootVisible(false);
    //myTree.setShowsRootHandles(false);

    ToolbarDecorator decorator = ToolbarDecorator.createDecorator(tree).disableUpDownActions();
    decorator.setAddAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        // show choose file dialog, add certificate
        FileChooser.chooseFile(CERTIFICATE_DESCRIPTOR, null, null, file -> {
          String path = file.getPath();
          X509Certificate certificate = CertificateUtil.loadX509Certificate(path);
          if (certificate == null) {
            Messages.showErrorDialog(myRootPanel, IdeBundle.message("settings.certificate.malformed.x509.server.certificate"),
                                     IdeBundle.message("settings.certificate.not.imported"));
          }
          else if (myCertificates.contains(certificate)) {
            Messages.showWarningDialog(myRootPanel, IdeBundle.message("settings.certificate.certificate.already.exists"),
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
          showCard(EMPTY_PANEL);
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
          showCard(getCardName(certificate));
        }
      }
    });

    myCertificatesListPanel.setBorder(
      IdeBorderFactory.createTitledBorder(IdeBundle.message("settings.certificate.accepted.certificates"), false, JBUI.insetsTop(8))
        .setShowLine(false));
    myCertificatesListPanel.add(decorator.createPanel(), BorderLayout.CENTER);

    TreeUtil.expandAll(tree);
  }

  private void showCard(@NotNull String cardName) {
    ((CardLayout)myDetailsPanel.getLayout()).show(myDetailsPanel, cardName);
  }

  private void addCertificatePanel(@NotNull X509Certificate certificate) {
    String uniqueName = getCardName(certificate);
    JPanel infoPanel = new CertificateInfoPanel(certificate);
    UIUtil.addInsets(infoPanel, UIUtil.PANEL_REGULAR_INSETS);
    JBScrollPane scrollPane = new JBScrollPane(infoPanel);
    //scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    myDetailsPanel.add(scrollPane, uniqueName);
  }

  private static String getCardName(@NotNull X509Certificate certificate) {
    return certificate.getSubjectX500Principal().getName();
  }

  @NotNull
  @Override
  public String getId() {
    return "http.certificates";
  }

  @Nls
  @Override
  public String getDisplayName() {
    return UIBundle.message("configurable.CertificateConfigurable.display.name");
  }

  @Nullable
  @Override
  public String getHelpTopic() {
    return "reference.idesettings.server.certificates";
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    // lazily initialized to ensure that disposeUIResources() will be called, if
    // tree builder was created
    initializeUI();
    return myRootPanel;
  }

  @Override
  public boolean isModified() {
    CertificateManager.Config state = CertificateManager.getInstance().getState();
    return myAcceptAutomatically.isSelected() != state.ACCEPT_AUTOMATICALLY ||
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
        throw new ConfigurationException(IdeBundle.message("settings.certificate.cannot.remove.certificate.for", getCommonName(certificate)),
                                         IdeBundle.message("settings.certificate.cannot.remove.certificate"));
      }
    }
    CertificateManager.Config state = CertificateManager.getInstance().getState();

    state.ACCEPT_AUTOMATICALLY = myAcceptAutomatically.isSelected();
  }

  @Override
  public void reset() {
    List<X509Certificate> original = myTrustManager.getCertificates();
    myTreeBuilder.reset(original);

    myCertificates.clear();
    myCertificates.addAll(original);

    myDetailsPanel.removeAll();
    myDetailsPanel.add(myEmptyPanel, EMPTY_PANEL);

    // fill lower panel with cards
    for (X509Certificate certificate : original) {
      addCertificatePanel(certificate);
    }

    if (!myCertificates.isEmpty()) {
      myTreeBuilder.selectFirstCertificate();
    }

    CertificateManager.Config state = CertificateManager.getInstance().getState();
    myAcceptAutomatically.setSelected(state.ACCEPT_AUTOMATICALLY);
  }

  @Override
  public void disposeUIResources() {
    Disposer.dispose(myTreeBuilder);
    myTrustManager.removeListener(this);
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
