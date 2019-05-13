// "Create field for parameter 'id'" "true"

class Person {
    private String __fname, __lname, __street;
    private final int id


    public Person ( String i_lname, String i_fname, String i_street)
    {
        __lname = i_lname;
        __fname = i_fname;
        __street = i_street;
    }
 
    public Person ( int id, String i_lname, String i_fname, String i_street)
    {
        this.id = id
        __lname = i_lname;
        __fname = i_fname;
        __street = i_street;
    }

}