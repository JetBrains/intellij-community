import java.util.stream.IntStream;

@SuppressWarnings({"SameParameterValue", "ResultOfMethodCallIgnored"})
public class ParameterHints {

    private ParameterHints() {
    }

    private String[] getWordsInSentence(String sentence) {
        return null;
    }

    public void startUserService() {
    }

    public Customer findCustomerByName(String name) {
        return null;
    }

    public void noParamNames() {
    }

    public void shortParameterNames() {

    }

    private Customer findCustomer(String name, String address, Integer orderNumber, int id, boolean ascending) {
        return new Customer(name, address, orderNumber, id, ascending);
    }

    private static class Service implements Runnable {

        Service(String path, int port) {

        }

        @Override
        public void run() {
            new Customer("customName", "/users/", 42, 1, false);
        }

        static void generateServiceName(String n) {

        }
    }

}
