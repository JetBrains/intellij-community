import org.jetbrains.annotations.Nls;

class SentenceCapitalization {

    @Nls(capitalization = Nls.Capitalization.Sentence)
    public String getName1() {
        return <warning descr="String '<b>hello</b>, <i>world!</i>' is not properly capitalized. It should have sentence capitalization">"<caret><b>hello</b>, <i>world!</i>"</warning>;
    }
}
