package org.jetbrains.intellij.plugins.journey.diagram;

import com.intellij.diagram.DiagramEdge;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.*;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase5;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class JourneyDiagramDataModelTest extends LightJavaCodeInsightFixtureTestCase5 {

  private @NotNull Project myProject;
  private JourneyDiagramDataModel myDataModel;;

  @BeforeEach
  public void setUp() {
    myProject = getFixture().getProject();
    myDataModel = new JourneyDiagramDataModel(myProject, new JourneyDiagramProvider());
  }

  @Test
  public void test() {
    JourneyDiagramDataModel model = myDataModel;
    JourneyNode nodeA = model.addElement(new JourneyNodeIdentity(createPsiMethod(myProject, "a")));
    assertNotNull(nodeA);
    JourneyNode nodeB = model.addElement(new JourneyNodeIdentity(createPsiMethod(myProject, "b")));
    assertNotNull(nodeB);
    DiagramEdge<JourneyNodeIdentity> edgeAB = model.createEdge(nodeA, nodeB);
    assertNotNull(edgeAB);
    assertEquals(2, model.getNodes().size());
    assertEquals(1, model.getEdges().size());
  }

  private static PsiMethod createPsiMethod(Project project, String method) {
    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
    String methodText = "public void " + method + "() {}";
    return ReadAction.compute(() -> {
      return elementFactory.createMethodFromText(methodText, null);
    });
  }
}
