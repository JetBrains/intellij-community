import groovy.beans.Bindable

class MyBean {
    @Bindable String Test
}

def bean = new MyBean()

bean.firePropertyChange(<caret>)