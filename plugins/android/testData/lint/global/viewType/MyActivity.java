package p1.p2;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ImageView;

public class MyActivity extends Activity
{
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        ImageView view = (ImageView) findViewById(R.id.txt);
        setContentView(view);
    }
}