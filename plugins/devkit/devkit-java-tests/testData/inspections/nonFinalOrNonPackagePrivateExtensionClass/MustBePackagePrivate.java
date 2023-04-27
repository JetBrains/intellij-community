import com.intellij.codeInspection.LocalInspectionTool;

<error descr="Class 'MyInspection' is public, should be declared in a file named 'MyInspection.java'">public final class <warning descr="Extension class should be package-private">MyInspection</warning> extends LocalInspectionTool</error> {
}