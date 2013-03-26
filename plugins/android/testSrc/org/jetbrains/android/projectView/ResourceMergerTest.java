package org.jetbrains.android.projectView;

import com.intellij.ide.projectView.impl.AbstractProjectViewPSIPane;
import com.intellij.openapi.module.Module;
import com.intellij.projectView.BaseProjectViewTestCase;
import com.intellij.psi.PsiDirectory;
import org.jetbrains.android.AndroidTestCase;

import java.io.IOException;

/**
 * @author yole
 */
public class ResourceMergerTest extends BaseProjectViewTestCase {
  @Override
  protected String getTestDataPath() {
    return AndroidTestCase.getAbsoluteTestDataPath();
  }

  @Override
  protected Module createMainModule() throws IOException {
    Module result = super.createMainModule();
    AndroidTestCase.addAndroidFacet(result, AndroidTestCase.getTestSdkPath());
    return result;
  }

  public void testMerger() {
    final AbstractProjectViewPSIPane pane = createPane();
    getProjectTreeStructure().setProviders(new ResourceMergerTreeStructureProvider());

    PsiDirectory directory = getContentDirectory();
    PsiDirectory resDirectory = directory.findSubdirectory("res");
    assertStructureEqual(resDirectory, "PsiDirectory: res\n" +
                                       " ResourceDirectory:values\n" +
                                       "  XmlFile:colors.xml\n" +
                                       "  XmlFile:strings.xml\n");
  }
}
