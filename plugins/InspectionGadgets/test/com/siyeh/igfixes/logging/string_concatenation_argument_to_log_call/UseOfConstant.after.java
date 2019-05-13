import org.slf4j.*;

class UseOfConstant {

    void foo() {
        Logger logger = LoggerFactory.getLogger(UseOfConstant.class);
        final String CONST = "const";
        String var = "var";
        logger.info("string {}" + CONST, var);
    }
}