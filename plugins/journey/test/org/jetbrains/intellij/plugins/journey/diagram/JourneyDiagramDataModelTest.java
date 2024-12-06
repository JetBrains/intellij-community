package org.jetbrains.intellij.plugins.journey.diagram;

import com.intellij.diagram.DiagramBuilder;
import com.intellij.diagram.DiagramDataKeys;
import com.intellij.diagram.DiagramEdge;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase5;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.intellij.plugins.journey.util.PsiUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Set;

import static org.jetbrains.intellij.plugins.journey.util.PsiUtil.createSmartPointer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class JourneyDiagramDataModelTest extends LightJavaCodeInsightFixtureTestCase5 {

  private @NotNull Project myProject;
  private JourneyDiagramDataModel myDataModel;;

  @BeforeEach
  public void setUp() {
    myProject = getFixture().getProject();
    myDataModel = new JourneyDiagramDataModel(myProject, new JourneyDiagramProvider());
    myDataModel.putUserData(DiagramDataKeys.GRAPH_BUILDER, Mockito.mock(DiagramBuilder.class));
  }

  @Test
  public void testCreateEdge() {
    JourneyDiagramDataModel model = myDataModel;
    PsiMethod methodA = createPsiMethod(myProject, "a");
    PsiElement methodAIdentifier = PsiUtil.tryFindIdentifier(methodA);
    JourneyNode nodeA = model.addElement(new JourneyNodeIdentity(createSmartPointer(methodAIdentifier)));
    assertNotNull(nodeA);
    PsiMethod methodB = createPsiMethod(myProject, "b");
    PsiElement methodBIdentifier = PsiUtil.tryFindIdentifier(methodB);
    JourneyNode nodeB = model.addElement(new JourneyNodeIdentity(createSmartPointer(methodBIdentifier)));
    assertNotNull(nodeB);
    DiagramEdge<JourneyNodeIdentity> edgeAB = model.createEdge(nodeA, nodeB);
    assertNotNull(edgeAB);
    assertEquals(2, model.getNodes().size());
    JourneyNode[] nodes = model.getNodes().toArray(new JourneyNode[0]);
    assertEquals(methodAIdentifier, nodes[0].getIdentifyingElement().getIdentifierElement());
    assertEquals(methodA, nodes[0].getIdentifyingElement().getMember());
    assertEquals(methodBIdentifier, nodes[1].getIdentifyingElement().getIdentifierElement());
    assertEquals(methodB, nodes[1].getIdentifyingElement().getMember());
    assertEquals(1, model.getEdges().size());
  }

  /**
   * [A] -> [B] -> [C]
   * delete(B)
   * [A] -> [C]
   */
  @Test
  void testDeleteTransitNode() {
    JourneyDiagramDataModel model = myDataModel;
    JourneyNode nodeA = model.addElement(new JourneyNodeIdentity(createSmartPointer(createPsiMethod(myProject, "a"))));
    assertNotNull(nodeA);
    JourneyNode nodeB = model.addElement(new JourneyNodeIdentity(createSmartPointer(createPsiMethod(myProject, "b"))));
    assertNotNull(nodeB);
    JourneyNode nodeC = model.addElement(new JourneyNodeIdentity(createSmartPointer(createPsiMethod(myProject, "c"))));
    assertNotNull(nodeC);
    DiagramEdge<JourneyNodeIdentity> edgeAB = model.createEdge(nodeA, nodeB);
    assertNotNull(edgeAB);
    DiagramEdge<JourneyNodeIdentity> edgeBC = model.createEdge(nodeB, nodeC);
    assertNotNull(edgeBC);
    model.removeNode(nodeB);

    Set<JourneyEdge> edges = model.getEdges();
    assertEquals(1, edges.size());
    JourneyEdge edge = (JourneyEdge)edges.toArray()[0];
    assertEquals(nodeA, edge.getSource());
    assertEquals(nodeC, edge.getTarget());
  }

  private static PsiMethod createPsiMethod(Project project, String method) {
    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
    String methodText = "public void " + method + "() {}";
    return ReadAction.compute(() -> {
      return elementFactory.createMethodFromText(methodText, null);
    });
  }
}
