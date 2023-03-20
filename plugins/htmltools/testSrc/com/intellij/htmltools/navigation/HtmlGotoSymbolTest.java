package com.intellij.htmltools.navigation;

import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.ide.util.gotoByName.GotoSymbolModel2;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;

public class HtmlGotoSymbolTest extends BasePlatformTestCase {
  public void testSimple() {
    myFixture.configureByText(HtmlFileType.INSTANCE, """
      <!doctype html>
      <html lang='en'>
      <head>
          <meta charset='UTF-8'>
          <meta content='width=device-width, user-scalable=no, initial-scale=1.0, maximum-scale=1.0, minimum-scale=1.0'
                name='viewport'>
          <meta http-equiv='X-UA-Compatible' content='ie=edge'>
          <title>Document</title>
      </head>
      <body>
      <div>
          <div id="hello"></div>
      </div>
      </body>
      </html>
      """);
    doTest("hello", "hello (aaa.html)");
  }

  private void doTest(@NotNull String name, String @NotNull ... expectedItems) {
    ((PsiManagerEx)myFixture.getPsiManager()).setAssertOnFileLoadingFilter(VirtualFileFilter.ALL, myFixture.getTestRootDisposable());

    GotoSymbolModel2 model = new GotoSymbolModel2(myFixture.getProject(), myFixture.getTestRootDisposable());

    assertContainsElements(Arrays.asList(model.getNames(false)), name);
    final ArrayList<String> actual = new ArrayList<>();
    for (Object o : model.getElementsByName(name, false, "")) {
      if (o instanceof NavigationItem) {
        final ItemPresentation presentation = ((NavigationItem)o).getPresentation();
        assertNotNull(presentation);
        actual.add(presentation.getPresentableText() + " " + presentation.getLocationString());
      }
    }
    assertSameElements(actual, expectedItems);
  }

}
