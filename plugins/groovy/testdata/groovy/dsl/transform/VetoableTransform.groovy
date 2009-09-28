import groovy.beans.Vetoable

class MyBean {
    @Vetoable String Test
}

def bean = new MyBean()

bean.fire<caret>