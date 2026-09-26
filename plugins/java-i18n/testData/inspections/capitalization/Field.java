import org.jetbrains.annotations.Nls;

class Test {
    final @Nls(capitalization = Nls.Capitalization.Title) String title = <warning descr="String 'Hello world!' is not properly capitalized. It should have title capitalization">"Hello world!"</warning>; 
}