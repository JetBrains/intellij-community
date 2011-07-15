package com.intellij.ide.plugins;

import com.intellij.openapi.application.PathManager;
import junit.framework.TestCase;

import java.io.File;

/**
 * @author Dmitry Avdeev
 *         Date: 7/14/11
 */
public class PluginDescriptorTest extends TestCase {

  public void testDescriptorLoading() throws Exception {
    String path = PathManager.getHomePath().replace(File.separatorChar, '/') + "/community/platform/platform-impl/testData/pluginDescriptor";
    IdeaPluginDescriptorImpl descriptor = PluginManager.loadDescriptorFromJar(new File(path + "/asp.jar"));
    assertNotNull(descriptor);
  }
}
