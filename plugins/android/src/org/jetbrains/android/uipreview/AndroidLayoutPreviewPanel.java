 package org.jetbrains.android.uipreview;

 import com.intellij.openapi.Disposable;
 import com.intellij.openapi.ui.Messages;
 import com.intellij.openapi.ui.VerticalFlowLayout;
 import com.intellij.openapi.util.Disposer;
 import com.intellij.ui.HyperlinkLabel;
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

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidLayoutPreviewPanel extends JPanel implements Disposable {
  private static final double EPS = 0.0000001;
  private static final double MAX_ZOOM_FACTOR = 2.0;
  private static final double ZOOM_STEP = 1.25;

  private RenderingErrorMessage myErrorMessage;
  private String myWarnMessage;
  private BufferedImage myImage;

  private final HyperlinkLabel myHyperlinkMessageLabel = new HyperlinkLabel("", Color.BLUE, getBackground(), Color.BLUE);
  private final JBLabel myMessageLabel = new JBLabel();

  private double myZoomFactor = 1.0;
  private boolean myZoomToFit = true;

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
    setBackground(Color.WHITE);
    setOpaque(true);
    myImagePanel.setBorder(BorderFactory.createMatteBorder(1, 1, 1, 1, Color.GRAY));

    myHyperlinkMessageLabel.addHyperlinkListener(new HyperlinkListener() {
      public void hyperlinkUpdate(final HyperlinkEvent e) {
        final Runnable quickFix = myErrorMessage.myQuickFix;
        if (quickFix != null && e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
          quickFix.run();
        }
      }
    });
    myHyperlinkMessageLabel.setOpaque(false);

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
    add(myHyperlinkMessageLabel);
    add(myMessageLabel);

    add(new MyImagePanelWrapper());
  }

  public void setImage(@Nullable final BufferedImage image, @NotNull final String fileName) {
    myImage = image;
    myFileNameLabel.setText(fileName);
    doRevalidate();
  }

  public void showProgress() {
    ((CardLayout)myProgressIconWrapper.getLayout()).show(myProgressIconWrapper, PROGRESS_ICON_CARD_NAME);
    myProgressIcon.setVisible(true);
    myProgressIcon.resume();
  }

  public void hideProgress() {
    myProgressIcon.suspend();
    ((CardLayout)myProgressIconWrapper.getLayout()).show(myProgressIconWrapper, EMPTY_CARD_NAME);
    myProgressIcon.setVisible(false);
  }

  private void doRevalidate() {
    revalidate();
    updateImageSize();
    repaint();
  }

  public void setErrorMessage(@Nullable RenderingErrorMessage errorMessage) {
    myErrorMessage = errorMessage;
  }

  public void setWarnMessage(String warnMessage) {
    myWarnMessage = warnMessage;
  }

  public void update() {
    myImagePanel.setVisible(true);

    myHyperlinkMessageLabel.setVisible(false);
    myMessageLabel.setVisible(false);

    if (myErrorMessage != null) {
      if (myErrorMessage.myLinkText.length() > 0 || myErrorMessage.myAfterLinkText.length() > 0) {
        myHyperlinkMessageLabel.setHyperlinkText(myErrorMessage.myBeforeLinkText,
                                      myErrorMessage.myLinkText,
                                      myErrorMessage.myAfterLinkText);
        myHyperlinkMessageLabel.setIcon(Messages.getErrorIcon());
        myHyperlinkMessageLabel.setVisible(true);
      }
      else {
        myMessageLabel.setText("<html><body>" + myErrorMessage.myBeforeLinkText.replace("\n", "<br>") + "</body></html>");
        myMessageLabel.setIcon(Messages.getErrorIcon());
        myMessageLabel.setVisible(true);
      }
    }

    if (myErrorMessage == null && myWarnMessage != null && myWarnMessage.length() > 0) {
      myMessageLabel.setText("<html><body>" + myWarnMessage.replace("\n", "<br>") + "</body></html>");
      myMessageLabel.setIcon(Messages.getWarningIcon());
      myMessageLabel.setVisible(true);
    }

    repaint();
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
    return (double) myImagePanel.getWidth() / (double) myImage.getWidth();
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
    return Math.min(1.0, (double) getParent().getWidth() / (double) myImage.getWidth());
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
