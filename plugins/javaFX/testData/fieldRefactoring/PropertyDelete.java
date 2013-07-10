import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;

class Test {
    private IntegerProperty c<caret>ount = new SimpleIntegerProperty(this, "count");

    public int getCount() {
        return count.get();
    }

    public IntegerProperty countProperty() {
        return count;
    }

    public void setCount(int count) {
        this.count.set(count);
    }
}