package com.intellij.htmltools.navigation;

import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.ide.util.gotoByName.GotoSymbolModel2;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.sharedCodeInsightFixture;
import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.sharedCodeInsightProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class HtmlGotoSymbolTest {
  private static final TestFixture<Project> projectFixture = sharedCodeInsightProjectFixture();
  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = sharedCodeInsightFixture(projectFixture);

  @Test
  public void testSimple() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
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
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    ((PsiManagerEx)myFixture.getPsiManager()).setAssertOnFileLoadingFilter(VirtualFileFilter.ALL, myFixture.getTestRootDisposable());

    GotoSymbolModel2 model = new GotoSymbolModel2(myFixture.getProject(), myFixture.getTestRootDisposable());

    assertThat(Arrays.asList(ReadAction.compute(() -> model.getNames(false)))).contains(name);
    final ArrayList<String> actual = new ArrayList<>();
    ReadAction.run(() -> {
      for (Object o : model.getElementsByName(name, false, "")) {
        if (o instanceof NavigationItem) {
          final ItemPresentation presentation = ((NavigationItem)o).getPresentation();
          assertNotNull(presentation);
          actual.add(presentation.getPresentableText() + " " + presentation.getLocationString());
        }
      }
    });
    assertThat(actual).hasSameElementsAs(Arrays.asList(expectedItems));
  }

}
