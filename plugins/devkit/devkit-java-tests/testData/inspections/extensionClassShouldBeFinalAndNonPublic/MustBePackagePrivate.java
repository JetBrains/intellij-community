import java.util.ArrayList;

<error descr="Class 'MyInspection' is public, should be declared in a file named 'MyInspection.java'">public final class <warning descr="Extension class should not be public">MyInspection<caret></warning> extends ArrayList<String></error> {
}