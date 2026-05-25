package a;

public class Testing {
    public static String test() {
        Service service = new Service();
        return service.<caret>
    }
}

// WITH_ORDER
// EXIST: getSize
// EXIST: fetchData
