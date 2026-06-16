import { PROVIDERS } from '../data'

export default function Providers() {
  return (
    <section className="section providers" id="providers">
      <div className="section-head">
        <h2>Works with your favorite agents</h2>
        <p>Switch providers per task — your sessions stay in one place.</p>
      </div>
      <ul className="provider-grid">
        {PROVIDERS.map((p) => (
          <li key={p.name} className="provider-chip">
            <img src={`/icons/${p.icon}`} alt="" width={28} height={28} />
            <span>{p.name}</span>
          </li>
        ))}
      </ul>
    </section>
  )
}
