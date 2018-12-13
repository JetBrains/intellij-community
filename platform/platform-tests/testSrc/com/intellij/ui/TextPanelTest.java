package com.intellij.ui;

import com.intellij.openapi.wm.impl.status.TextPanel;
import org.junit.Assert;
import org.junit.Test;

import java.awt.*;

/**
 * @author Denis Fokin
 */
public class TextPanelTest {

  @Test
  public void testSetPreferredSizeSetExplicitSizeGetExplicitSize() {
    final Dimension preferredDimension = new Dimension(2345,678);
    final Dimension explicitDimension = new Dimension(4343,5554);

    TextPanel tp = new TextPanel() {};
    tp.setPreferredSize(preferredDimension);
    tp.setExplicitSize(explicitDimension);

    Assert.assertEquals("Explicit dimension is more preferable",
                        explicitDimension, tp.getPreferredSize());
  }
}
