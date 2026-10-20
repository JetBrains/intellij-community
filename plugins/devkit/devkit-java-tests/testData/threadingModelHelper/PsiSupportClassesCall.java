import com.intellij.extapi.psi.ASTDelegatePsiElement;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiTypeParameterListOwner;
import com.intellij.psi.stubs.StubElement;

class PsiSupportClassesCall {
  void testMethod(PsiTypeParameterListOwner owner, PsiReference reference, StubElement stub,
                  ASTDelegatePsiElement delegate, ASTWrapperPsiElement wrapper) {
    ownerHelper(owner);
    referenceHelper(reference);
    stubHelper(stub);
    delegateHelper(delegate);
    wrapperHelper(wrapper);
  }

  void ownerHelper(PsiTypeParameterListOwner owner) {
    owner.getTypeParameterList();
  }

  void referenceHelper(PsiReference reference) {
    reference.resolve();
  }

  void stubHelper(StubElement stub) {
    stub.getPsi();
  }

  void delegateHelper(ASTDelegatePsiElement delegate) {
    delegate.getChildren();
  }

  void wrapperHelper(ASTWrapperPsiElement wrapper) {
    wrapper.getNode();
  }
}