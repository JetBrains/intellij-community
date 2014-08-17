package unit.classes;

public class TestClassFields {

    private static int[] sizes;
    private static String[] names;

    static {

    	names = new String[]{"name1", "name2"};
    	sizes = new int[names.length];
    }
}
