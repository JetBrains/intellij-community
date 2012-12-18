/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.android.uipreview;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.AsyncProcessIcon;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidLayoutPreviewPanel extends JPanel implements Disposable {
  private static final double EPS = 0.0000001;
  private static final double MAX_ZOOM_FACTOR = 2.0;
  private static final double ZOOM_STEP = 1.25;

  private FixableIssueMessage myErrorMessage;
  private List<FixableIssueMessage> myWarnMessages;

  private BufferedImage myImage;

  private final JPanel myMessagesPanel = new JPanel(new VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 0, true, false));

  private double myZoomFactor = 1.0;
  private boolean myZoomToFit = true;

  private final List<ProgressIndicator> myProgressIndicators = new ArrayList<ProgressIndicator>();
  private boolean myProgressVisible = false;
  private boolean myShowWarnings = false;

  private final JPanel myImagePanel = new JPanel() {
    @Override
    public void paintComponent(Graphics g) {
      super.paintComponent(g);
      if (myImage == null) {
        return;
      }
      final Dimension scaledDimension = getScaledDimension();
      final Graphics2D g2 = (Graphics2D)g;
      g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.drawImage(myImage, 0, 0, scaledDimension.width, scaledDimension.height, 0, 0, myImage.getWidth(), myImage.getHeight(), null);
    }
  };

  private AsyncProcessIcon myProgressIcon;
  @NonNls private static final String PROGRESS_ICON_CARD_NAME = "Progress";
  @NonNls private static final String EMPTY_CARD_NAME = "Empty";
  private JPanel myProgressIconWrapper = new JPanel();
  private final JBLabel myFileNameLabel = new JBLabel();

  public AndroidLayoutPreviewPanel() {
    super(new VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 0, true, true));
    setBackground(JBColor.WHITE);
    setOpaque(true);
    myImagePanel.setBorder(BorderFactory.createMatteBorder(1, 1, 1, 1, JBColor.GRAY));

    myFileNameLabel.setHorizontalAlignment(SwingConstants.CENTER);
    myFileNameLabel.setBorder(new EmptyBorder(5, 0, 5, 0));

    final JPanel progressPanel = new JPanel();
    progressPanel.setLayout(new BoxLayout(progressPanel, BoxLayout.X_AXIS));
    myProgressIcon = new AsyncProcessIcon("Android layout rendering");
    myProgressIconWrapper.setLayout(new CardLayout());
    myProgressIconWrapper.add(PROGRESS_ICON_CARD_NAME, myProgressIcon);
    myProgressIconWrapper.add(EMPTY_CARD_NAME, new JBLabel(" "));
    myProgressIconWrapper.setOpaque(false);

    Disposer.register(this, myProgressIcon);
    progressPanel.add(myProgressIconWrapper);
    progressPanel.add(new JBLabel(" "));
    progressPanel.setOpaque(false);

    final JPanel titlePanel = new JPanel(new BorderLayout());
    titlePanel.setOpaque(false);
    titlePanel.add(myFileNameLabel, BorderLayout.CENTER);
    titlePanel.add(progressPanel, BorderLayout.EAST);

    ((CardLayout)myProgressIconWrapper.getLayout()).show(myProgressIconWrapper, EMPTY_CARD_NAME);

    add(titlePanel);

    myMessagesPanel.setBorder(IdeBorderFactory.createEmptyBorder(0, 5, 0, 5));
    myMessagesPanel.setOpaque(false);
    add(myMessagesPanel);

    add(new MyImagePanelWrapper());
  }

  public void setImage(@Nullable final BufferedImage image, @NotNull final String fileName) {
    myImage = image;
    myFileNameLabel.setText(fileName);
    doRevalidate();
  }

  public synchronized void registerIndicator(@NotNull ProgressIndicator indicator) {
    synchronized (myProgressIndicators) {
      myProgressIndicators.add(indicator);

      if (!myProgressVisible) {
        myProgressVisible = true;
        ((CardLayout)myProgressIconWrapper.getLayout()).show(myProgressIconWrapper, PROGRESS_ICON_CARD_NAME);
        myProgressIcon.setVisible(true);
        myProgressIcon.resume();
      }
    }
  }

  public void unregisterIndicator(@NotNull ProgressIndicator indicator) {
    synchronized (myProgressIndicators) {
      myProgressIndicators.remove(indicator);

      if (myProgressIndicators.size() == 0 && myProgressVisible) {
        myProgressVisible = false;
        myProgressIcon.suspend();
        ((CardLayout)myProgressIconWrapper.getLayout()).show(myProgressIconWrapper, EMPTY_CARD_NAME);
        myProgressIcon.setVisible(false);
      }
    }
  }

  private void doRevalidate() {
    revalidate();
    updateImageSize();
    repaint();
  }

  public void setErrorMessage(@Nullable FixableIssueMessage errorMessage) {
    myErrorMessage = errorMessage;
  }

  public void setWarnMessages(@Nullable List<FixableIssueMessage> warnMessages) {
    myWarnMessages = warnMessages;
  }

  public void update() {
    myImagePanel.setVisible(true);
    myMessagesPanel.removeAll();

    if (myErrorMessage != null) {
      showMessage(myErrorMessage, Messages.getErrorIcon(), myMessagesPanel);
    }
    if (myWarnMessages != null && myWarnMessages.size() > 0) {
      final HyperlinkLabel showHideWarnsLabel = new HyperlinkLabel();
      showHideWarnsLabel.setOpaque(false);
      final String showMessage = "Show " + myWarnMessages.size() + " warnings";
      final String hideMessage = "Hide " + myWarnMessages.size() + " warnings";
      showHideWarnsLabel.setHyperlinkText("", myShowWarnings ? hideMessage : showMessage, "");

      final JPanel warningsPanel = new JPanel(new VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 0, true, false));
      warningsPanel.setOpaque(false);

      showHideWarnsLabel.addHyperlinkListener(new HyperlinkListener() {
        public void hyperlinkUpdate(final HyperlinkEvent e) {
          if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
            myShowWarnings = !warningsPanel.isVisible();
            warningsPanel.setVisible(myShowWarnings);
            showHideWarnsLabel.setHyperlinkText("", myShowWarnings ? hideMessage : showMessage, "");
          }
        }
      });
      final JPanel wrapper = new JPanel(new BorderLayout());
      wrapper.setOpaque(false);
      wrapper.add(showHideWarnsLabel);
      wrapper.setBorder(IdeBorderFactory.createEmptyBorder(5, 0, 5, 0));
      myMessagesPanel.add(wrapper);

      for (FixableIssueMessage warnMessage : myWarnMessages) {
        showMessage(warnMessage, Messages.getWarningIcon(), warningsPanel);
      }
      warningsPanel.setVisible(myShowWarnings);
      myMessagesPanel.add(warningsPanel);
    }
    revalidate();
    repaint();
  }

  private static void showMessage(final FixableIssueMessage message, Icon icon, JPanel panel) {
    if (message.myLinkText.length() > 0 || message.myAfterLinkText.length() > 0) {
      final HyperlinkLabel warnLabel = new HyperlinkLabel();
      warnLabel.setOpaque(false);
      warnLabel.setHyperlinkText(message.myBeforeLinkText,
                                 message.myLinkText,
                                 message.myAfterLinkText);
      warnLabel.setIcon(icon);

      warnLabel.addHyperlinkListener(new HyperlinkListener() {
        public void hyperlinkUpdate(final HyperlinkEvent e) {
          final Runnable quickFix = message.myQuickFix;
          if (quickFix != null && e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
            quickFix.run();
          }
        }
      });
      panel.add(warnLabel);
    }
    else {
      final JBLabel warnLabel = new JBLabel();
      warnLabel.setOpaque(false);
      warnLabel.setText("<html><body>" + message.myBeforeLinkText.replace("\n", "<br>") + "</body></html>");
      warnLabel.setIcon(icon);
      panel.add(warnLabel);
    }
    if (message.myAdditionalFixes.size() > 0) {
      final JPanel fixesPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
      fixesPanel.setBorder(IdeBorderFactory.createEmptyBorder(3, 0, 10, 0));
      fixesPanel.setOpaque(false);
      fixesPanel.add(Box.createHorizontalStrut(icon.getIconWidth()));

      for (Pair<String, Runnable> pair : message.myAdditionalFixes) {
        final HyperlinkLabel fixLabel = new HyperlinkLabel();
        fixLabel.setOpaque(false);
        fixLabel.setHyperlinkText(pair.getFirst());
        final Runnable fix = pair.getSecond();

        fixLabel.addHyperlinkListener(new HyperlinkListener() {
          @Override
          public void hyperlinkUpdate(HyperlinkEvent e) {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
              fix.run();
            }
          }
        });
        fixesPanel.add(fixLabel);
      }
      panel.add(fixesPanel);
    }
  }

  void updateImageSize() {
    if (myImage == null) {
      myImagePanel.setSize(0, 0);
    }
    else {
      myImagePanel.setSize(getScaledDimension());
    }
  }

  private Dimension getScaledDimension() {
    if (myZoomToFit) {
      final Dimension panelSize = getParent().getSize();
      if (myImage.getWidth() <= panelSize.width && myImage.getHeight() <= panelSize.height) {
        return new Dimension(myImage.getWidth(), myImage.getHeight());
      }

      if (myImage.getWidth() <= panelSize.width) {
        final double f = panelSize.getHeight() / myImage.getHeight();
        return new Dimension((int)(myImage.getWidth() * f), (int)(myImage.getHeight() * f));
      }
      else if (myImage.getHeight() <= panelSize.height) {
        final double f = panelSize.getWidth() / myImage.getWidth();
        return new Dimension((int)(myImage.getWidth() * f), (int)(myImage.getHeight() * f));
      }

      double f = panelSize.getWidth() / myImage.getWidth();
      int candidateWidth = (int)(myImage.getWidth() * f);
      int candidateHeight = (int)(myImage.getHeight() * f);
      if (candidateWidth <= panelSize.getWidth() && candidateHeight <= panelSize.getHeight()) {
        return new Dimension(candidateWidth, candidateHeight);
      }
      f = panelSize.getHeight() / myImage.getHeight();
      return new Dimension((int)(myImage.getWidth() * f), (int)(myImage.getHeight() * f));
    }
    return new Dimension((int)(myImage.getWidth() * myZoomFactor), (int)(myImage.getHeight() * myZoomFactor));
  }

  private void setZoomFactor(double zoomFactor) {
    myZoomFactor = zoomFactor;
    doRevalidate();
  }

  private double computeCurrentZoomFactor() {
    if (myImage == null) {
      return myZoomFactor;
    }
    return (double)myImagePanel.getWidth() / (double)myImage.getWidth();
  }

  private double getZoomFactor() {
    return myZoomToFit ? computeCurrentZoomFactor() : myZoomFactor;
  }

  public void zoomOut() {
    setZoomFactor(Math.max(getMinZoomFactor(), myZoomFactor / ZOOM_STEP));
  }

  public boolean canZoomOut() {
    return myImage != null && myZoomFactor > getMinZoomFactor() + EPS;
  }

  private double getMinZoomFactor() {
    return Math.min(1.0, (double)getParent().getWidth() / (double)myImage.getWidth());
  }

  public void zoomIn() {
    if (myZoomToFit) {
      myZoomToFit = false;
      setZoomFactor(computeCurrentZoomFactor() * ZOOM_STEP);
      return;
    }
    setZoomFactor(myZoomFactor * ZOOM_STEP);
  }

  public boolean canZoomIn() {
    return getZoomFactor() * ZOOM_STEP < MAX_ZOOM_FACTOR - EPS;
  }

  public void zoomActual() {
    if (myImage == null) {
      return;
    }
    if (myZoomToFit && myImagePanel.getWidth() >= myImage.getWidth() && myImagePanel.getHeight() >= myImage.getHeight()) {
      return;
    }
    myZoomToFit = false;
    setZoomFactor(1.0);
  }

  public void setZoomToFit(boolean zoomToFit) {
    myZoomToFit = zoomToFit;
    doRevalidate();
  }

  public boolean isZoomToFit() {
    return myZoomToFit;
  }

  @Override
  public void dispose() {
  }

  private class MyImagePanelWrapper extends JLayeredPane {
    public MyImagePanelWrapper() {
      add(myImagePanel);
    }

    private void centerComponents() {
      Rectangle bounds = getBounds();
      Point point = myImagePanel.getLocation();
      point.x = (bounds.width - myImagePanel.getWidth()) / 2;
      myImagePanel.setLocation(point);
    }

    public void invalidate() {
      centerComponents();
      super.invalidate();
    }

    public Dimension getPreferredSize() {
      return myImagePanel.getSize();
    }
  }
}
