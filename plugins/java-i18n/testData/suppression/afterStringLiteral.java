// "Suppress for statement with comment" "true"
class C {
    {
        //noinspection SpellCheckingInspection
        String s = "tyypoo";
        System.out.println(s);
    }
}