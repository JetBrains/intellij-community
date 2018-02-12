package com.intellij.ui;

import junit.framework.TestCase;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

/**
 * @author Dennis.Ushakov
 */
public class CustomProtocolHandlerTest extends TestCase {
  public void testOpenOurLink() throws URISyntaxException {
    final URI uri = new URI("x-mine://open?file=/Users/denofevil/RubymineProjects/JRubyRails/app/assets/javascripts/application.js.coffee&line=2");
    final List<String> args = new CustomProtocolHandler().getOpenArgs(uri);
    assertTrue(args.contains("--line"));
    assertTrue(args.contains("2"));
    assertTrue(args.contains("/Users/denofevil/RubymineProjects/JRubyRails/app/assets/javascripts/application.js.coffee"));
  }
}
