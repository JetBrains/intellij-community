package com.siyeh.ig.bugs;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiAnonymousClass;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.SerializationUtils;
import org.jetbrains.annotations.NotNull;

public class ComparatorNotSerializableInspection extends ClassInspection {

    public String getDisplayName(){
        return InspectionGadgetsBundle.message(
                "comparator.not.serializable.display.name");
    }

    public String getGroupDisplayName(){
        return GroupNames.BUGS_GROUP_NAME;
    }

    @NotNull
    public String buildErrorString(Object... infos){
        return InspectionGadgetsBundle.message(
                "comparator.not.serializable.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor(){
        return new ComparatorNotSerializableVisitor();
    }

    private static class ComparatorNotSerializableVisitor extends BaseInspectionVisitor{

        public void visitClass(PsiClass aClass) {
            //note, no call to super, avoiding drilldown
            if (aClass instanceof PsiAnonymousClass) {
                return;
            }
            if (!ClassUtils.isSubclass(aClass, "java.util.Comparator")) {
                return;
            }
            if (SerializationUtils.isSerializable(aClass)) {
                return;
            }
            registerClassError(aClass);
        }
    }
}
