//region Test configuration
// - hidden: line markers
//endregion
package client;

import sample.MppException;

public class Main {
    public static void main(String[] args) throws MppException {
        MppException mppException = new MppException("failure");
        Throwable throwable = mppException;
        Exception exception = mppException;
        String message = mppException.getMessage();
        throw mppException;
    }
}
